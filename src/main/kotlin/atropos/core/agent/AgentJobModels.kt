package atropos.core.agent

import java.nio.file.Path
import java.time.Instant

enum class AgentJobStatus {
    PLANNING,
    PATCHING,
    APPLYING,
    REPAIRING,
    COMPLETED,
    FAILED,
    REFUSED
}

data class AgentJobRecord(
    val id: String,
    val task: String,
    val status: AgentJobStatus,
    val provider: String,
    val patchId: String? = null,
    val appliedPatchId: String? = null,
    val verificationId: String? = null,
    val repairId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val planAt: Instant? = null,
    val patchAt: Instant? = null,
    val applyAt: Instant? = null,
    val verificationAt: Instant? = null,
    val repairAt: Instant? = null,
    val result: String? = null,
    val failureReason: String? = null,
    val plan: String? = null,
    val patchResult: String? = null,
    val applyResult: String? = null,
    val repairResult: String? = null,
    val smokeCommand: String? = null,
    val smokeExitCode: Int? = null,
    val smokeDurationMillis: Long? = null,
    val smokeStdout: String? = null,
    val smokeStderr: String? = null,
    val smokePassed: Boolean? = null,
    val smokeResult: String? = null,
    val finalReport: String? = null,
    val commitProposal: String? = null,
    val nextSuggestedCommand: String? = null,
    val contextExportPath: String? = null,
    val metaFile: Path
) {
    fun render(): String = buildString {
        appendLine("job id: $id")
        appendLine("task: $task")
        appendLine("status: $status")
        appendLine("provider: $provider")
        appendLine("patch id: ${patchId ?: "none"}")
        appendLine("applied patch id: ${appliedPatchId ?: "none"}")
        appendLine("verification id: ${verificationId ?: "none"}")
        appendLine("repair id: ${repairId ?: "none"}")
        appendLine("created at: $createdAt")
        appendLine("updated at: $updatedAt")
        appendLine("started at: $startedAt")
        appendLine("finished at: ${finishedAt ?: "none"}")
        appendLine("plan at: ${planAt ?: "none"}")
        appendLine("patch at: ${patchAt ?: "none"}")
        appendLine("apply at: ${applyAt ?: "none"}")
        appendLine("verification at: ${verificationAt ?: "none"}")
        appendLine("repair at: ${repairAt ?: "none"}")
        appendLine("result: ${result ?: "none"}")
        appendLine("failure reason: ${failureReason ?: "none"}")
        appendLine("smoke command: ${smokeCommand ?: "none"}")
        appendLine("smoke exit code: ${smokeExitCode ?: "none"}")
        appendLine("smoke duration ms: ${smokeDurationMillis ?: "none"}")
        appendLine("smoke passed: ${smokePassed ?: "none"}")
        appendLine("smoke result: ${smokeResult ?: "none"}")
        appendLine("smoke stdout: ${smokeStdout ?: "none"}")
        appendLine("smoke stderr: ${smokeStderr ?: "none"}")
        appendLine("final report: ${finalReport ?: "none"}")
        appendLine("commit proposal: ${commitProposal ?: "none"}")
        appendLine("next suggested command: ${nextSuggestedCommand ?: "none"}")
        appendLine("context export path: ${contextExportPath ?: "none"}")
        appendLine("record file: $metaFile")
        renderBlock("plan", plan)?.let { appendLine(it) }
        renderBlock("patch result", patchResult)?.let { appendLine(it) }
        renderBlock("apply result", applyResult)?.let { appendLine(it) }
        renderBlock("repair result", repairResult)?.let { appendLine(it) }
        renderBlock("final report", finalReport)?.let { appendLine(it) }
        renderBlock("commit proposal", commitProposal)?.let { appendLine(it) }
    }.trimEnd()

    fun renderSummaryLine(): String = buildString {
        append("$id | $status | provider=$provider | patch=${patchId ?: "none"}")
        appliedPatchId?.takeIf { it.isNotBlank() && it != patchId }?.let { append(" applied=$it") }
        verificationId?.takeIf { it.isNotBlank() }?.let { append(" verify=$it") }
        repairId?.takeIf { it.isNotBlank() }?.let { append(" repair=$it") }
        smokeResult?.takeIf { it.isNotBlank() }?.let { append(" smoke=${truncate(it, 60)}") }
        finalReport?.takeIf { it.isNotBlank() }?.let { append(" final=${truncate(it, 60)}") }
        nextSuggestedCommand?.takeIf { it.isNotBlank() }?.let { append(" next=${truncate(it, 60)}") }
        append(" | ${truncate(task, 72)}")
        failureReason?.takeIf { it.isNotBlank() }?.let { append(" | failure=${truncate(it, 72)}") }
    }

    private fun renderBlock(label: String, value: String?): String? {
        val text = value?.trimEnd()?.takeIf { it.isNotBlank() } ?: return null
        return buildString {
            appendLine("${label}:")
            text.lineSequence().forEach { line -> appendLine("  $line") }
        }.trimEnd()
    }

    private fun truncate(text: String, maxChars: Int): String {
        val collapsed = text.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= maxChars) return collapsed
        return collapsed.take(maxChars - 3) + "..."
    }
}
