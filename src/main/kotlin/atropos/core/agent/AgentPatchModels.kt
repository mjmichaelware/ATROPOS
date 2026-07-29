package atropos.core.agent

import atropos.core.policy.AgencyDisposition
import atropos.core.security.RedactionFilter
import java.nio.file.Path
import java.time.Instant

data class AgentPatchRecord(
    val id: String,
    val provider: String,
    val createdAt: Instant,
    val task: String,
    val contextBytes: Int,
    val diffBytes: Int,
    val patchDir: Path,
    val diffFile: Path,
    val metaFile: Path
)

data class AgentPatchCheckResult(
    val passed: Boolean,
    val exitCode: Int,
    val output: String,
    /**
     * How bounded agency disposed of the proposal. Carried so a refusal is a
     * typed outcome a compositor can act on — an `APPROVAL_REQUIRED` patch is
     * something an operator could still authorise, which a bare exit code
     * cannot express. `null` where no proposal was made.
     */
    val disposition: AgencyDisposition? = null,
    val proposalId: String? = null
) {
    val statusText: String
        get() = if (passed) "OK" else "FAILED"
}

data class AgentPatchSnapshot(
    val id: String,
    val patchFile: Path,
    val metaFile: Path,
    val diffText: String,
    val extraction: AgentPatchExtraction
)

data class AgentPatchApplyResult(
    val patchId: String?,
    val patchFile: Path?,
    val changedPaths: List<String> = emptyList(),
    val checkOnly: Boolean,
    val applied: Boolean,
    val checkResult: AgentPatchCheckResult? = null,
    val verificationResult: AgentVerificationRunResult? = null,
    val applyExitCode: Int? = null,
    val applyOutput: String? = null,
    val refusalReason: String? = null,
    val logFile: Path? = null,
    /** Bounded-agency disposition of the mutation proposal; `null` if none was made. */
    val disposition: AgencyDisposition? = null,
    val proposalId: String? = null
) {
    fun render(): String = buildString {
        val filter = RedactionFilter()
        appendLine("Patch id: ${patchId ?: "none"}")
        appendLine("Patch path: ${patchFile ?: "none"}")
        appendLine("Changed paths: ${changedPaths.joinToString(", ") { filter.redact(it) }.ifBlank { "none" }}")
        if (checkOnly) {
            appendLine(
                if (checkResult?.passed == true && refusalReason.isNullOrBlank()) {
                    "APPLY CHECK OK"
                } else {
                    "APPLY CHECK FAILED: ${filter.redact(refusalReason ?: checkResult?.output ?: "unknown")}"
                }
            )
        } else {
            appendLine(
                if (applied) {
                    "APPLY OK"
                } else {
                    "APPLY REFUSED: ${filter.redact(refusalReason ?: checkResult?.output ?: "unknown")}"
                }
            )
        }
        checkResult?.let {
            appendLine("git apply --check: ${it.statusText}${it.output.takeIf { output -> output.isNotBlank() }?.let { output -> " :: ${filter.redact(output)}" } ?: ""}")
        }
        verificationResult?.let {
            appendLine("verification patch id: ${it.patchId ?: "none"}")
            it.verificationId?.let { id -> appendLine("verification id: $id") }
            it.command?.let { command -> appendLine("verification command: ${filter.redact(command)}") }
            appendLine("verification changed paths: ${it.changedPaths.joinToString(", ") { path -> filter.redact(path) }.ifBlank { "none" }}")
            it.exitCode?.let { exit -> appendLine("verification exit code: $exit") }
            if (it.durationMillis > 0) appendLine("verification duration ms: ${it.durationMillis}")
            appendLine("verification result: ${if (it.passed) "PASSED" else "FAILED"}")
            it.metaFile?.let { meta -> appendLine("verification metadata: $meta") }
            it.refusalReason?.takeIf { reason -> reason.isNotBlank() }?.let { reason -> appendLine("verification refusal reason: ${filter.redact(reason)}") }
        }
        applyExitCode?.let { appendLine("git apply exit code: $it") }
        logFile?.let { appendLine("Apply log: $it") }
        if (applied) {
            val verifyCommand = if (changedPaths.isNotEmpty()) {
                "git diff -- ${changedPaths.joinToString(" ")}"
            } else {
                "git status --short"
            }
            appendLine("Next command to verify: $verifyCommand")
        }
        refusalReason?.takeIf { it.isNotBlank() && !checkOnly }?.let { appendLine("Refusal reason: ${filter.redact(it)}") }
    }.trimEnd()
}
