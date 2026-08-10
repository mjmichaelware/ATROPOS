package atropos.core.agent

import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter

internal class AgentPatchHandler(
    private val collector: AgentContextCollector,
    private val selector: AgentProviderSelector,
    private val patchStore: AgentPatchStore,
    private val queueService: AgentQueueService,
    private val memoryStore: LocalMemoryStore,
    private val redactionFilter: RedactionFilter,
    private val patchCascadeRunner: AgentPatchCascadeRunner,
    private val failureSummary: AgentFailureSummary
) {
    fun handle(
        activeProviderName: String,
        task: String,
        patchProviderOverride: String? = null
    ): AgentPatchRunResult {
        val snapshot = collector.collectPatch(task)
        val selection = selector.select(activeProviderName, patchProviderOverride)
        val prompt = redactionFilter.redact(task.trim())
        val sourceRefusal = AgentProviderContextBoundary.validateSourcePack(
            context = snapshot.text,
            sourcePackId = snapshot.sourcePackId,
            fetchReceiptId = snapshot.fetchReceiptId,
            sourcePackContentHash = snapshot.sourcePackContentHash,
            sourceTreeHash = snapshot.sourceTreeHash,
            sourceBindingKind = snapshot.sourceBindingKind
        )
        if (sourceRefusal != null) {
            val reason = sourceRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_patch",
                subjectId = null,
                title = "agent patch source context unavailable",
                body = reason,
                tags = listOf("agent", "patch", "source-pack", "blocked")
            )
            return AgentPatchRunResultFactory.localFailure(
                providerName = "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = false,
                failureSummary = reason,
                rejectionReason = reason
            )
        }

        return try {
            val cascade = patchCascadeRunner.run(selection.patchOrder, prompt, snapshot.text, snapshot.truncated)
            val queued = cascade.failure?.result?.takeIf { it.queued }
            if (queued != null) {
                val queueRecord = queueService.enqueueUnavailable(
                    prompt,
                    retryAtEpochMs = queued.earliestRetryEpochMs
                )
                val reason = queued.queueReason ?: "all patch providers unavailable"
                val queueMessage = queueRecord?.let { "patch queued as ${it.id}" }
                    ?: "patch deferred; local queue persistence unavailable"
                memoryStore.rememberRoute(
                    subjectId = queueRecord?.id ?: "local_queue",
                    title = "agent patch queued after provider exhaustion",
                    body = "task=$prompt\nreason=$reason\n$queueMessage",
                    tags = listOf("agent", "patch", "queue", "degraded")
                )
                return AgentPatchRunResultFactory.localFailure(
                    providerName = "local_queue",
                    contextByteCount = snapshot.byteCount,
                    retryAttempted = false,
                    failureSummary = reason,
                    rejectionReason = queueMessage
                )
            }
            val acceptance = cascade.success ?: return AgentPatchRunResultFactory.localFailure(
                providerName = cascade.failure?.result?.providerName ?: selection.patchOrder.firstOrNull() ?: "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = cascade.failure?.retryAttempted ?: false,
                failureSummary = cascade.failure?.rejectionReason ?: "provider response did not include a usable unified diff",
                rejectionReason = cascade.failure?.rejectionReason,
                responsePreview = cascade.failure?.responsePreview
            )

            val result = acceptance.result
            val extraction = acceptance.extraction
            val normalizedDiff = patchStore.normalizeProviderDiff(extraction.diff)

            val record = patchStore.createRecord(
                provider = result.providerName,
                task = prompt,
                contextBytes = snapshot.byteCount,
                diff = normalizedDiff
            )
            val check = patchStore.runGitApplyCheck(record.diffFile)
            patchStore.writeMeta(record, check)
            memoryStore.rememberRoute(
                subjectId = result.providerName,
                title = "agent patch route",
                body = "task=${prompt.trim()}\nprovider=${result.providerName}\npatch=${record.id}\ncheck=${check.statusText}\nsourcePack=${snapshot.sourcePackId ?: "none"}\nfetchReceipt=${snapshot.fetchReceiptId ?: "none"}",
                tags = listOf("agent", "patch", "route")
            )

            AgentPatchRunResult(
                providerName = result.providerName,
                contextByteCount = snapshot.byteCount,
                diffByteCount = record.diffBytes,
                patchId = record.id,
                patchPath = record.diffFile,
                checkResult = check,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        } catch (failure: Exception) {
            memoryStore.rememberFailure(
                subjectType = "agent_patch",
                subjectId = null,
                title = "agent patch failed",
                body = failureSummary.compact(failure.message),
                tags = listOf("agent", "patch", "failure")
            )
            AgentPatchRunResultFactory.localFailure(
                providerName = "local_fallback",
                contextByteCount = snapshot.byteCount,
                retryAttempted = false,
                failureSummary = failureSummary.compact(failure.message)
            )
        }
    }
}
