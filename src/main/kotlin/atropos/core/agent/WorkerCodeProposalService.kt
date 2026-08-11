package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.territory.TerritoryEnforcer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Fan-out boundary for model-authored code proposals.
 *
 * Each worker proposal is routed through [AgentService.patch], which owns
 * source binding, redaction, provider policy, patch storage, and git-apply
 * checking. This service never applies a patch and therefore cannot approve
 * its own output.
 */
class WorkerCodeProposalService(
    agentService: AgentService? = null,
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val canonicalAgentService = agentService ?: AgentService(
        collector = AgentContextCollector(repoRoot = repoRoot)
    )
    private val fileHasher = SelfHostFileHasher()
    private val normalizedRepoRoot = repoRoot.toAbsolutePath().normalize()

    fun propose(
        workerId: String,
        activeProvider: String,
        task: String,
        territory: List<String>
    ): WorkerCodeProposal {
        val refusal = validate(workerId, activeProvider, task, territory)
        if (refusal != null) return WorkerCodeProposal.refused(workerId, refusal)

        val scopedTask = buildString {
            appendLine("worker_id=$workerId")
            appendLine("territory=${territory.joinToString(",")}")
            append(task.trim())
        }
        val result = canonicalAgentService.patch(activeProvider.trim(), scopedTask)
        val patchPath = result.patchPath
        val accepted = result.patchId != null && result.checkResult?.passed == true &&
            patchPath != null && Files.isRegularFile(patchPath)
        if (!accepted) {
            return WorkerCodeProposal(
                workerId = workerId,
                provider = result.providerName,
                patchId = result.patchId,
                patchPath = patchPath,
                territory = territory,
                accepted = false,
                proposalSha256 = null,
                reason = result.failureSummary ?: result.rejectionReason ?: "worker proposal was not accepted"
            )
        }
        val acceptedPath = patchPath ?: return WorkerCodeProposal.refused(
            workerId,
            "worker proposal refused: accepted result has no patch path"
        )
        val normalizedPatchPath = acceptedPath.toAbsolutePath().normalize()
        if (!normalizedPatchPath.startsWith(normalizedRepoRoot) || hasSymbolicComponent(normalizedPatchPath)) {
            return WorkerCodeProposal(
                workerId = workerId,
                provider = result.providerName,
                patchId = result.patchId,
                patchPath = acceptedPath,
                territory = territory,
                accepted = false,
                proposalSha256 = null,
                reason = "worker proposal refused: proposal evidence escaped repository root"
            )
        }
        val extraction = AgentPatchExtractor().extract(Files.readString(acceptedPath))
        val touchedPaths = extraction?.touchedPaths.orEmpty()
        if (extraction == null || touchedPaths.isEmpty()) {
            return WorkerCodeProposal(
                workerId = workerId,
                provider = result.providerName,
                patchId = result.patchId,
                patchPath = acceptedPath,
                territory = territory,
                accepted = false,
                proposalSha256 = null,
                reason = "worker proposal refused: patch has no parseable touched paths"
            )
        }
        val outside = TerritoryEnforcer(territory).firstOutside(touchedPaths)
        if (outside != null) {
            return WorkerCodeProposal(
                workerId = workerId,
                provider = result.providerName,
                patchId = result.patchId,
                patchPath = acceptedPath,
                territory = territory,
                accepted = false,
                proposalSha256 = null,
                reason = "worker proposal refused: path outside territory: $outside"
            )
        }
        val proposalSha256 = fileHasher.sha256(acceptedPath)
            ?: return WorkerCodeProposal(
                workerId = workerId,
                provider = result.providerName,
                patchId = result.patchId,
                patchPath = acceptedPath,
                territory = territory,
                accepted = false,
                proposalSha256 = null,
                reason = "worker proposal refused: proposal evidence disappeared before hashing"
            )
        val patchId = result.patchId ?: return WorkerCodeProposal.refused(
            workerId,
            "worker proposal refused: accepted result has no patch id"
        )
        val verification = canonicalAgentService.verify(patchId)
        if (!verification.passed) {
            return WorkerCodeProposal(
                workerId = workerId,
                provider = result.providerName,
                patchId = result.patchId,
                patchPath = acceptedPath,
                territory = territory,
                accepted = false,
                proposalSha256 = proposalSha256,
                verification = verification,
                reason = "worker proposal refused: independent verification failed: " +
                    (verification.refusalReason ?: "exit=${verification.exitCode ?: "none"}")
            )
        }
        return WorkerCodeProposal(
            workerId = workerId,
            provider = result.providerName,
            patchId = result.patchId,
            patchPath = acceptedPath,
            territory = territory,
            accepted = true,
            proposalSha256 = proposalSha256,
            verification = verification,
            reason = "proposal stored, independently verified, and mutation not performed"
        )
    }

    fun proposeBatch(
        activeProvider: String,
        workers: List<WorkerCodeTask>
    ): List<WorkerCodeProposal> {
        val reservedTerritories = mutableListOf<Pair<String, String>>()
        val seenWorkerIds = mutableSetOf<String>()
        return workers
            .sortedBy { it.workerId }
            .mapIndexed { index, task ->
                val identity = task.workerId.trim().lowercase(Locale.ROOT)
                if (index >= MAX_BATCH_WORKERS) {
                    WorkerCodeProposal(
                        workerId = task.workerId,
                        provider = activeProvider,
                        patchId = null,
                        patchPath = null,
                        territory = task.territory,
                        accepted = false,
                        proposalSha256 = null,
                        reason = "worker proposal refused: batch exceeds $MAX_BATCH_WORKERS workers"
                    )
                } else if (!seenWorkerIds.add(identity)) {
                    WorkerCodeProposal(
                        workerId = task.workerId,
                        provider = activeProvider,
                        patchId = null,
                        patchPath = null,
                        territory = task.territory,
                        accepted = false,
                        proposalSha256 = null,
                        reason = "worker proposal refused: duplicate worker id"
                    )
                } else {
                    val normalized = task.territory.map(::normalizeTerritory)
                    val overlap = normalized
                        .flatMap { candidate -> reservedTerritories.map { candidate to it.second } }
                        .firstOrNull { (candidate, reserved) -> territoriesOverlap(candidate, reserved) }
                    if (overlap != null) {
                        WorkerCodeProposal(
                            workerId = task.workerId,
                            provider = activeProvider,
                            patchId = null,
                            patchPath = null,
                            territory = task.territory,
                            accepted = false,
                            proposalSha256 = null,
                            reason = "worker proposal refused: territory overlaps ${overlap.second}"
                        )
                    } else {
                        val proposal = propose(task.workerId, activeProvider, task.task, task.territory)
                        if (proposal.accepted) {
                            normalized.forEach { path -> reservedTerritories += task.workerId to path }
                        }
                        proposal
                    }
                }
            }
    }

    private fun validate(workerId: String, provider: String, task: String, territory: List<String>): String? {
        if (workerId.isBlank()) return "worker id is required"
        if (workerId != workerId.trim() || workerId.length > MAX_WORKER_ID_CHARS) {
            return "worker id is invalid"
        }
        if (workerId.any { it.isISOControl() }) return "worker id contains control characters"
        if (provider.isBlank()) return "active provider is required"
        if (task.isBlank()) return "worker task is required"
        if (task.length > MAX_TASK_CHARS) return "worker task exceeds $MAX_TASK_CHARS characters"
        if (territory.isEmpty()) return "worker territory is required"
        if (territory.size > MAX_TERRITORY_ENTRIES) {
            return "worker territory exceeds $MAX_TERRITORY_ENTRIES entries"
        }
        if (territory.any(::isInvalidTerritory)) {
            return "worker territory is invalid"
        }
        val normalized = territory.map(::normalizeTerritory)
        if (normalized.any(String::isBlank) || normalized.toSet().size != normalized.size) {
            return "worker territory contains duplicate or empty normalized paths"
        }
        return null
    }

    private fun normalizeTerritory(path: String): String = path.replace('\\', '/').trim().trim('/')

    private fun isInvalidTerritory(path: String): Boolean {
        val normalized = normalizeTerritory(path)
        return path.isBlank() ||
            path.startsWith("/") ||
            path.contains('\\') ||
            path.indexOf('\u0000') >= 0 ||
            normalized.isBlank() ||
            normalized == "." ||
            normalized == ".." ||
            normalized.split('/').any { segment -> segment.isBlank() || segment == "." || segment == ".." }
    }

    private fun territoriesOverlap(left: String, right: String): Boolean =
        left == right || left.startsWith("$right/") || right.startsWith("$left/")

    private fun hasSymbolicComponent(path: Path): Boolean {
        var cursor: Path? = path
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) return true
            cursor = cursor.parent
        }
        return false
    }

    private companion object {
        const val MAX_BATCH_WORKERS = 8
        const val MAX_WORKER_ID_CHARS = 128
        const val MAX_TASK_CHARS = 16_384
        const val MAX_TERRITORY_ENTRIES = 32
    }
}

data class WorkerCodeTask(
    val workerId: String,
    val task: String,
    val territory: List<String>
)

data class WorkerCodeProposal(
    val workerId: String,
    val provider: String,
    val patchId: String?,
    val patchPath: Path?,
    val territory: List<String>,
    val accepted: Boolean,
    val proposalSha256: String?,
    val verification: AgentVerificationRunResult? = null,
    val reason: String
) {
    companion object {
        fun refused(workerId: String, reason: String): WorkerCodeProposal = WorkerCodeProposal(
            workerId = workerId,
            provider = "none",
            patchId = null,
            patchPath = null,
            territory = emptyList(),
            accepted = false,
            proposalSha256 = null,
            verification = null,
            reason = reason
        )
    }
}
