/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.file.Files
import java.nio.file.Path

data class FactoryRunHandoffState(
    val runId: String,
    val dagId: String,
    val acceptanceFreezeSha256: String,
    val openWork: Int,
    val nextRunnableAtomIds: List<String>,
    val blockedAtomIds: List<String>,
    val failedAtomIds: List<String>,
    val doneAtomIds: List<String>,
    val lastGoodCommit: String?,
    val stopReason: String?,
    val evidencePath: Path? = null
)

data class FactoryResumeContext(
    val handoff: FactoryRunHandoffState,
    val promptFingerprint: String,
    val promptArtifact: Path,
    val requirementsArtifact: Path,
    val planArtifact: Path,
    val acceptanceFreezeArtifact: Path,
    val acceptanceFreeze: FactoryAcceptanceFreeze
)

/** A journal-aligned projection of durable DAG/checkpoint state for resume. */
object FactoryRunHandoff {
    fun write(
        repoRoot: Path,
        runId: String,
        dagId: String,
        snapshot: FactoryObligationSnapshot,
        freeze: FactoryAcceptanceFreeze,
        lastGoodCommit: String? = null,
        evidencePath: String? = null
    ): Path {
        val path = repoRoot.resolve(".atropos/runs/$runId/factory-handoff.md").normalize()
        require(path.startsWith(repoRoot.toAbsolutePath().normalize())) { "factory handoff escaped repository" }
        val content = buildString {
            appendLine("schema=factory-handoff-v1")
            appendLine("run_id=$runId")
            appendLine("planning_dag=$dagId")
            appendLine("acceptance_freeze_sha256=${freeze.sha256}")
            appendLine("open_work=${snapshot.openWork}")
            appendLine("next_runnable_atoms=${snapshot.runnableAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("blocked_atoms=${snapshot.blockedAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("failed_atoms=${snapshot.failedAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("done_atoms=${snapshot.doneAtomIds.joinToString(",").ifBlank { "none" }}")
            appendLine("last_good_commit=${lastGoodCommit ?: "none"}")
            appendLine("evidence_path=${evidencePath ?: "none"}")
            appendLine("stop_reason=${snapshot.stopReason ?: "none"}")
        }
        writeAtomically(path, content)
        return path
    }

    fun read(repoRoot: Path, runId: String): FactoryRunHandoffState {
        require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "factory run id is invalid" }
        val path = repoRoot.resolve(".atropos/runs/$runId/factory-handoff.md").normalize()
        require(path.startsWith(repoRoot.toAbsolutePath().normalize())) { "factory handoff escaped repository" }
        require(Files.isRegularFile(path)) { "factory handoff not found: $runId" }
        val fields = Files.readAllLines(path).mapNotNull { line ->
            line.substringBefore('=').takeIf { it.isNotBlank() }?.let { it to line.substringAfter('=') }
        }.toMap()
        require(fields["schema"] == "factory-handoff-v1") { "unsupported factory handoff schema" }
        require(fields["run_id"] == runId) { "factory handoff run id mismatch" }
        fun list(name: String): List<String> = fields[name].orEmpty().split(',').map(String::trim).filter { it.isNotBlank() && it != "none" }
        val normalizedRoot = repoRoot.toAbsolutePath().normalize()
        val evidencePath = fields["evidence_path"]?.takeUnless { it == "none" || it.isBlank() }?.let {
            Path.of(it).toAbsolutePath().normalize().also { candidate ->
                require(candidate.startsWith(normalizedRoot)) { "factory handoff evidence escaped repository" }
            }
        }
        return FactoryRunHandoffState(
            runId = runId,
            dagId = requireNotNull(fields["planning_dag"]),
            acceptanceFreezeSha256 = requireNotNull(fields["acceptance_freeze_sha256"]),
            openWork = requireNotNull(fields["open_work"]).toInt(),
            nextRunnableAtomIds = list("next_runnable_atoms"),
            blockedAtomIds = list("blocked_atoms"),
            failedAtomIds = list("failed_atoms"),
            doneAtomIds = list("done_atoms"),
            lastGoodCommit = fields["last_good_commit"]?.takeUnless { it == "none" },
            stopReason = fields["stop_reason"]?.takeUnless { it == "none" },
            evidencePath = evidencePath
        )
    }

    fun readContext(repoRoot: Path, runId: String): FactoryResumeContext {
        val handoff = read(repoRoot, runId)
        val runRoot = repoRoot.resolve(".atropos/research/factory/$runId").normalize()
        val prompt = runRoot.resolve("user-prompt.md")
        val requirements = runRoot.resolve("requirements.md")
        val plan = runRoot.resolve("plan.md")
        val freezePath = runRoot.resolve("acceptance-freeze.md")
        val normalizedRoot = repoRoot.toAbsolutePath().normalize()
        require(prompt.startsWith(normalizedRoot) && requirements.startsWith(normalizedRoot) && plan.startsWith(normalizedRoot) && freezePath.startsWith(normalizedRoot)) {
            "factory resume artifacts escaped repository"
        }
        require(Files.isRegularFile(prompt) && Files.isRegularFile(requirements) && Files.isRegularFile(plan) && Files.isRegularFile(freezePath)) {
            "factory resume cannot continue: attested prompt/requirements/plan/freeze artifacts are missing for $runId"
        }
        val fingerprint = Files.readAllLines(prompt).firstOrNull { it.startsWith("prompt_fingerprint=") }
            ?.substringAfter('=')?.trim().orEmpty()
        require(fingerprint.matches(Regex("prompt-[0-9a-f]{16}"))) {
            "factory resume cannot continue: prompt fingerprint is missing or malformed"
        }
        val freezeDocument = Files.readString(freezePath)
        val freeze = FactoryAcceptanceFreeze(freezeDocument, FactoryLineage.sha256(freezeDocument))
        require(freeze.sha256 == handoff.acceptanceFreezeSha256) {
            "factory resume cannot continue: acceptance freeze hash does not match handoff"
        }
        return FactoryResumeContext(handoff, fingerprint, prompt, requirements, plan, freezePath, freeze)
    }
}
