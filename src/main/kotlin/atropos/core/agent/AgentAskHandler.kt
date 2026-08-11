package atropos.core.agent

import atropos.core.ProviderCascadeRouter
import atropos.core.memory.LocalMemoryStore
import atropos.core.provider.ContextAttestationService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.provider.ProviderTruthService
import atropos.core.security.RedactionFilter

internal class AgentAskHandler(
    private val router: ProviderCascadeRouter,
    private val selector: AgentProviderSelector,
    private val collector: AgentContextCollector,
    private val jobStore: AgentJobStore,
    private val queueService: AgentQueueService,
    private val memoryStore: LocalMemoryStore,
    private val providerTruthService: ProviderTruthService,
    private val redactionFilter: RedactionFilter,
    private val answers: AgentAskAnswerNormalizer,
    private val failureSummary: AgentFailureSummary,
    private val attestationRetry: AgentAskAttestationRetry,
    private val enforceProviderPolicy: (String, String, String) -> Unit
) {
    fun handle(
        activeProviderName: String,
        task: String,
        contextOverride: AgentAskContextOverride? = null
    ): AgentRunResult {
        val selection = selector.select(activeProviderName)
        val sanitizedTask = redactionFilter.redact(task.trim())
        val providerId = selection.askOrder.firstOrNull()
            ?: activeProviderName.trim().lowercase().takeIf { it.isNotBlank() }
            ?: providerTruthService.snapshot().selectedProvider.trim().takeIf { it.isNotBlank() }
            ?: ""
        val envelope = contextOverride?.envelope?.let { AgentContextSnapshotAdapter.forProvider(it, providerId) }
            ?: ContextEnvelopeFactory.createSimple(
                providerId = providerId,
                modelId = "",
                task = sanitizedTask,
                repoRoot = collector.repoRoot
            )
        val snapshot = contextOverride?.let { AgentContextSnapshotAdapter.toSnapshot(it, collector.repoRoot) } ?: collector.collect(sanitizedTask)
        val envelopeRefusal = AgentProviderContextBoundary.validateEnvelope(envelope, collector.repoRoot)
        if (envelopeRefusal != null) {
            val reason = envelopeRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask context envelope refused",
                body = reason,
                tags = listOf("agent", "ask", "context", "blocked")
            )
            return AgentRunResult(
                providerName = "none",
                answerText = reason,
                contextByteCount = snapshot.byteCount,
                failureSummary = reason,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        }
        val sourceContextRefusal = AgentSourceContextRequirement.refusalFor(
            operation = "ask",
            task = sanitizedTask,
            sourcePackId = snapshot.sourcePackId,
            fetchReceiptId = snapshot.fetchReceiptId,
            sourcePackContentHash = snapshot.sourcePackContentHash,
            sourceTreeHash = snapshot.sourceTreeHash,
            sourceBindingKind = snapshot.sourceBindingKind,
            context = snapshot.text,
            truncated = snapshot.truncated
        )
        if (sourceContextRefusal != null) {
            val reason = sourceContextRefusal.message
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask source context refused: ${sourceContextRefusal.code}",
                body = reason,
                tags = listOf("agent", "ask", "source-pack", "blocked")
            )
            return AgentRunResult(
                providerName = "local_fallback",
                answerText = "$reason\n${answers.fallbackAnswer(sanitizedTask, snapshot)}",
                contextByteCount = snapshot.byteCount,
                failureSummary = reason,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        }
        return try {
            val result = router.completeWithCascade(
                requestedProvider = providerId,
                prompt = sanitizedTask,
                context = AgentPromptContract.buildWithEnvelope(
                    context = snapshot.text,
                    envelope = envelope
                ),
                providerOrderOverride = selection.askOrder,
                beforeAttempt = { provider -> enforceProviderPolicy(provider, sanitizedTask, "ask") },
                contextEnvelope = envelope
            )

            if (result.queued) {
                val queueRecord = queueService.enqueueUnavailable(
                    sanitizedTask,
                    retryAtEpochMs = result.earliestRetryEpochMs
                )
                val retryAt = result.earliestRetryEpochMs?.toString() ?: "unknown"
                val queueMessage = if (queueRecord != null) {
                    "all eligible providers unavailable; request queued as ${queueRecord.id} " +
                        "for retry after $retryAt"
                } else {
                    "all eligible providers unavailable; local fallback remains available " +
                        "for retry after $retryAt"
                }
                memoryStore.rememberRoute(
                    subjectId = queueRecord?.id ?: "local_queue",
                    title = "agent ask queued after provider exhaustion",
                    body = "task=$sanitizedTask\nreason=${result.queueReason ?: "provider unavailable"}\nretryAt=$retryAt",
                    tags = listOf("agent", "ask", "queue", "degraded")
                )
                return AgentRunResult(
                    providerName = "local_queue",
                    answerText = queueMessage,
                    contextByteCount = snapshot.byteCount,
                    failureSummary = result.queueReason ?: "provider unavailable",
                    sourcePackId = snapshot.sourcePackId,
                    fetchReceiptId = snapshot.fetchReceiptId
                )
            }

            val verified = ContextAttestationService.verify(envelope, result.response)
            val displayText: String
            val providerDisplayName: String
            val contextAttested: Boolean
            when (verified) {
                is ContextAttestationService.VerifiedResult.Accepted -> {
                    displayText = verified.cleanedResponse
                    providerDisplayName = result.providerName
                    contextAttested = true
                }
                is ContextAttestationService.VerifiedResult.Rejected -> {
                    memoryStore.rememberFailure(
                        subjectType = "context_failure",
                        subjectId = null,
                        title = verified.failure.javaClass.simpleName,
                        body = "${verified.failure.providerId}: ${verified.failure.reason}",
                        tags = listOf("context", "attestation", "failure")
                    )
                    val retryResult = attestationRetry.retry(providerId, sanitizedTask, snapshot.text, envelope)
                    if (retryResult != null) {
                        displayText = answers.present(retryResult.response)
                        providerDisplayName = retryResult.providerName
                    } else {
                        displayText = answers.fallbackAnswer(sanitizedTask, snapshot)
                        providerDisplayName = "local_fallback"
                    }
                    contextAttested = false
                }
            }

            memoryStore.rememberRoute(
                subjectId = providerDisplayName,
                title = "agent ask route",
                body = "task=$sanitizedTask\nprovider=$providerDisplayName\nsourcePack=${snapshot.sourcePackId ?: "none"}\nfetchReceipt=${snapshot.fetchReceiptId ?: "none"}",
                tags = listOf("agent", "ask", "route")
            )

            AgentRunResult(
                providerName = providerDisplayName,
                answerText = answers.present(displayText),
                contextByteCount = snapshot.byteCount,
                contextAttested = contextAttested,
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        } catch (failure: Exception) {
            memoryStore.rememberFailure(
                subjectType = "agent_ask",
                subjectId = null,
                title = "agent ask failed",
                body = failureSummary.compact(failure.message),
                tags = listOf("agent", "ask", "failure")
            )
            AgentRunResult(
                providerName = "local_fallback",
                answerText = answers.fallbackAnswer(sanitizedTask, snapshot),
                contextByteCount = snapshot.byteCount,
                failureSummary = failureSummary.compact(failure.message),
                sourcePackId = snapshot.sourcePackId,
                fetchReceiptId = snapshot.fetchReceiptId
            )
        }
    }
}
