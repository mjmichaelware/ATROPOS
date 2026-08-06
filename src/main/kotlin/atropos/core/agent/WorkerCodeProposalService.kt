package atropos.core.agent

import atropos.core.territory.TerritoryEnforcer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Fan-out boundary for model-authored code proposals.
 *
 * Each worker proposal is routed through [AgentService.patch], which owns
 * source binding, redaction, provider policy, patch storage, and git-apply
 * checking. This service never applies a patch and therefore cannot approve
 * its own output.
 */
class WorkerCodeProposalService(
    private val agentService: AgentService = AgentService()
) {
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
        val result = agentService.patch(activeProvider.trim(), scopedTask)
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
        return WorkerCodeProposal(
            workerId = workerId,
            provider = result.providerName,
            patchId = result.patchId,
            patchPath = acceptedPath,
            territory = territory,
            accepted = true,
            proposalSha256 = sha256(acceptedPath),
            reason = "proposal stored and git-apply checked; mutation not performed"
        )
    }

    fun proposeBatch(
        activeProvider: String,
        workers: List<WorkerCodeTask>
    ): List<WorkerCodeProposal> {
        val reservedTerritories = mutableListOf<Pair<String, String>>()
        return workers
            .distinctBy { it.workerId }
            .sortedBy { it.workerId }
            .map { task ->
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

    private fun validate(workerId: String, provider: String, task: String, territory: List<String>): String? {
        if (workerId.isBlank()) return "worker id is required"
        if (provider.isBlank()) return "active provider is required"
        if (task.isBlank()) return "worker task is required"
        if (territory.isEmpty()) return "worker territory is required"
        if (territory.any { path -> path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains('\\') }) {
            return "worker territory is invalid"
        }
        return null
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

    private fun normalizeTerritory(path: String): String = path.replace('\\', '/').trim().trim('/')

    private fun territoriesOverlap(left: String, right: String): Boolean =
        left == right || left.startsWith("$right/") || right.startsWith("$left/")
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
            reason = reason
        )
    }
}
