package atropos.core.agent

import java.nio.file.Path

data class AgentStatusSnapshot(
    val activeProvider: String,
    val providerOrder: List<String>,
    val patchProviderOrder: List<String>,
    val repoRoot: Path,
    val patchDirectory: Path,
    val lastPatchId: String?,
    val contextCapBytes: Int,
    val ownsRepoReadWrite: Boolean,
    val paidAutomaticModeLocked: Boolean,
    val localFallbackEnabled: Boolean,
    val doctorTruthSource: String,
    val knownActiveProviders: List<String>,
    val providerTruthReport: String
) {
    fun render(): String = buildString {
        appendLine("agent status:")
        appendLine("  active provider: $activeProvider")
        appendLine("  provider order for /agent ask: ${providerOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  provider order for /agent patch: ${patchProviderOrder.joinToString(" -> ").ifBlank { "none" }}")
        appendLine("  repo root: $repoRoot")
        appendLine("  patch directory: $patchDirectory")
        appendLine("  last patch id: ${lastPatchId ?: "none"}")
        appendLine("  context cap bytes: $contextCapBytes")
        appendLine("  repo ownership: ${if (ownsRepoReadWrite) "ATROPOS owns repo read/write; providers only see bounded context" else "unknown"}")
        appendLine("  paid automatic mode: ${if (paidAutomaticModeLocked) "locked" else "unlocked"}")
        appendLine("  local fallback: ${if (localFallbackEnabled) "enabled" else "disabled"}")
        appendLine("  last doctor truth source: $doctorTruthSource")
        appendLine("  known active doctor providers: ${knownActiveProviders.joinToString(", ")}")
        appendLine(providerTruthReport.prependIndent("  "))
    }.trimEnd()
}

data class AgentRunResult(
    val providerName: String,
    val answerText: String,
    val contextByteCount: Int,
    val failureSummary: String? = null,
    val contextAttested: Boolean = false,
    val sourcePackId: String? = null,
    val fetchReceiptId: String? = null
) {
    fun render(): String = buildString {
        appendLine("Provider used: $providerName")
        appendLine("context bytes: $contextByteCount")
        failureSummary?.takeIf { it.isNotBlank() }?.let {
            appendLine("fallback summary: $it")
        }
        sourcePackId?.takeIf { it.isNotBlank() }?.let { appendLine("source pack: $it") }
        fetchReceiptId?.takeIf { it.isNotBlank() }?.let { appendLine("fetch receipt: $it") }
        appendLine("answer:")
        appendLine(answerText.trimEnd())
    }.trimEnd()
}

data class AgentPatchRunResult(
    val providerName: String,
    val contextByteCount: Int,
    val diffByteCount: Int,
    val patchId: String?,
    val patchPath: Path?,
    val checkResult: AgentPatchCheckResult?,
    val retryAttempted: Boolean = false,
    val rejectionReason: String? = null,
    val responsePreview: String? = null,
    val failureSummary: String? = null,
    val sourceVerificationId: String? = null,
    val sourcePackId: String? = null,
    val fetchReceiptId: String? = null,
    val message: String? = null
) {
    fun render(): String = buildString {
        appendLine("Patch id: ${patchId ?: "none"}")
        appendLine("Provider used: $providerName")
        appendLine("Context bytes: $contextByteCount")
        appendLine("Diff bytes: $diffByteCount")
        appendLine("Patch path: ${patchPath ?: "none"}")
        appendLine("Retry attempted: ${if (retryAttempted) "yes" else "no"}")
        if (patchId == null) {
            rejectionReason?.takeIf { it.isNotBlank() }?.let { appendLine("Rejection reason: $it") }
            responsePreview?.takeIf { it.isNotBlank() }?.let { appendLine("Response preview: $it") }
        }
        appendLine(
            when (val result = checkResult) {
                null -> "Patch check: NOT RUN"
                else -> {
                    val output = result.output.takeIf { value -> value.isNotBlank() }
                    "Patch check: ${result.statusText}${output?.let { compact -> " :: $compact" } ?: ""}"
                }
            }
        )
        sourceVerificationId?.takeIf { it.isNotBlank() }?.let { appendLine("Source verification: $it") }
        sourcePackId?.takeIf { it.isNotBlank() }?.let { appendLine("Source pack: $it") }
        fetchReceiptId?.takeIf { it.isNotBlank() }?.let { appendLine("Fetch receipt: $it") }
        failureSummary?.takeIf { it.isNotBlank() }?.let { appendLine("fallback summary: $it") }
        message?.takeIf { it.isNotBlank() }?.let { appendLine(it.trimEnd()) }
    }.trimEnd()
}
