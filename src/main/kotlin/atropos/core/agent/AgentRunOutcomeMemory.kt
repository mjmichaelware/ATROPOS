package atropos.core.agent

import atropos.core.memory.LocalMemoryStore

class AgentRunOutcomeMemory(
    private val memoryStore: LocalMemoryStore
) {
    fun rememberFinalOutcome(
        record: AgentJobRecord,
        changedFiles: List<String>,
        sourceEvidence: SourceEvidence,
        impactedSymbols: List<String>
    ) {
        memoryStore.rememberJob(
            subjectId = record.id,
            title = "agent job finalized",
            body = buildString {
                appendLine("status=${record.status}")
                appendLine("provider=${record.provider}")
                appendLine("patch=${record.appliedPatchId ?: record.patchId ?: "none"}")
                appendLine("verification=${record.verificationId ?: "none"}")
                appendLine("smoke=${record.smokeResult ?: "none"}")
                appendLine("source=${sourceEvidence.describe()}")
                appendLine("impacted=${impactedSymbols.joinToString(", ").ifBlank { "none" }}")
                appendLine("changed=${changedFiles.joinToString(", ").ifBlank { "none" }}")
                appendLine("failure=${record.failureReason ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "job", record.status.name.lowercase())
        )
    }
}
