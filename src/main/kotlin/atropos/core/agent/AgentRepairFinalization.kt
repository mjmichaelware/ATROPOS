package atropos.core.agent

import atropos.core.memory.LocalMemoryStore

/**
 * Finalizes repair results and stores accepted repairs.
 *
 * Takes cascade results and stores the accepted diff, writing apply check results.
 */
internal class AgentRepairFinalization(
    private val memoryStore: LocalMemoryStore
) {
    /**
     * Stores the repaired diff and records the route that produced it.
     *
     * The `git apply --check` result is written into the patch metadata before
     * this returns, so a repair that cannot be applied is on record as such
     * rather than being discovered at apply time.
     */
    fun finalizeRepair(
        cascadeResult: AgentPatchCascadeResult,
        repairContext: AgentRepairPromptContext,
        contextByteCount: Int,
        sourcePackId: String?,
        fetchReceiptId: String?,
        sourceVerificationId: String,
        patchStore: AgentPatchStore
    ): AgentPatchRunResult {
        val queued = cascadeResult.failure?.result?.takeIf { it.queued }
        if (queued != null) {
            val reason = queued.queueReason ?: "all repair providers unavailable"
            val queueMessage = "repair deferred; all providers exhausted"
            memoryStore.rememberFailure(
                subjectType = "context_failure",
                subjectId = null,
                title = "agent repair queued after provider exhaustion",
                body = "verification=$sourceVerificationId\nreason=$reason\n$queueMessage",
                tags = listOf("agent", "repair", "queue", "degraded")
            )
            return AgentPatchRunResultFactory.localFailure(
                providerName = "local_queue",
                contextByteCount = contextByteCount,
                retryAttempted = false,
                failureSummary = reason,
                rejectionReason = queueMessage
            )
        }
        val acceptance = cascadeResult.success ?: return AgentPatchRunResultFactory.localFailure(
            providerName = cascadeResult.failure?.result?.providerName
                ?: "local_fallback",
            contextByteCount = contextByteCount,
            retryAttempted = cascadeResult.failure?.retryAttempted ?: false,
            failureSummary = cascadeResult.failure?.rejectionReason
                ?: "provider response did not include a usable unified diff",
            rejectionReason = cascadeResult.failure?.rejectionReason,
            responsePreview = cascadeResult.failure?.responsePreview
        )

        val record = patchStore.createRecord(
            provider = acceptance.result.providerName,
            task = "repair from verification $sourceVerificationId",
            contextBytes = contextByteCount,
            diff = acceptance.extraction.diff
        )
        val check = patchStore.runGitApplyCheck(record.diffFile)
        patchStore.writeMeta(record, check)
        memoryStore.rememberRepair(
            subjectId = record.id,
            title = "agent repair route",
            body = buildString {
                appendLine("verification=$sourceVerificationId")
                appendLine("provider=${acceptance.result.providerName}")
                appendLine("patch=${record.id}")
                appendLine("check=${check.statusText}")
                appendLine("sourcePack=${sourcePackId ?: "none"}")
                append("fetchReceipt=${fetchReceiptId ?: "none"}")
            },
            tags = listOf("agent", "repair", "route")
        )

        return AgentPatchRunResult(
            providerName = acceptance.result.providerName,
            contextByteCount = contextByteCount,
            diffByteCount = record.diffBytes,
            patchId = record.id,
            patchPath = record.diffFile,
            checkResult = check,
            retryAttempted = acceptance.retryAttempted,
            sourceVerificationId = sourceVerificationId,
            sourcePackId = sourcePackId,
            fetchReceiptId = fetchReceiptId
        )
    }
}
