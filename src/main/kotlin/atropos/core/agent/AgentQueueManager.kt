package atropos.core.agent

import atropos.core.AtroposConfig

/**
 * Manages queue state operations and basic queue queries.
 *
 * Handles enqueue/dequeue operations, state management, and queue inspection.
 */
internal class AgentQueueManager(
    private val config: AtroposConfig,
    private val collector: AgentContextCollector,
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(collector.repoRoot),
    private val store: AgentQueueStore = AgentQueueStore(collector.repoRoot),
    private val agencyGate: atropos.core.policy.BoundedAgencyGate = atropos.core.policy.BoundedAgencyGate(
        atropos.core.policy.ExecutionPolicyEngine(collector.repoRoot)
    ),
    private val memoryStore: atropos.core.memory.LocalMemoryStore = atropos.core.memory.LocalMemoryStore(
        collector.repoRoot.resolve(".atropos/memory").toFile()
    )
) {
    fun enqueue(task: String, smokeCommand: String? = null): AgentQueueRecord {
        enforceQueuePolicy("enqueue", task)
        return persistEnqueue(task, smokeCommand)
    }

    fun persistEnqueue(
        task: String,
        smokeCommand: String? = null,
        nextEligibleAt: java.time.Instant? = null
    ): AgentQueueRecord {
        val smoke = smokeCommand?.trim()?.takeIf { it.isNotBlank() }
        val refusal = smoke?.let { smokeRunner.validate(it) }
        if (refusal != null) {
            return store.createEntry(
                task = task,
                smokeCommand = smoke,
                state = AgentQueueState.REFUSED,
                checkpoint = AgentQueueCheckpoint.FINALIZED,
                provider = "none",
                failureReason = refusal
            ).also { rememberQueueSafely(it, "enqueue refused") }
        }
        return store.createEntry(task = task, smokeCommand = smoke, nextEligibleAt = nextEligibleAt)
            .also { rememberQueueSafely(it, "enqueued") }
    }

    fun enqueueUnavailable(task: String, retryAtEpochMs: Long? = null): AgentQueueRecord? {
        // Authorization is a hard boundary. Only persistence failures may
        // degrade to the local fallback used by provider-exhaustion callers.
        enforceQueuePolicy("enqueue", task)
        val nextEligibleAt = retryAtEpochMs?.let { runCatching { java.time.Instant.ofEpochMilli(it) }.getOrNull() }
        return runCatching { persistEnqueue(task, nextEligibleAt = nextEligibleAt) }.getOrNull()
    }

    fun list(limit: Int = 20): List<AgentQueueRecord> = store.listEntries(limit)

    fun resolve(reference: String): AgentQueueRecord? = store.resolve(reference)

    fun latest(): AgentQueueRecord? = store.latest()

    fun cancel(reference: String, reason: String = "operator cancelled queue entry"): AgentQueueRecord? {
        enforceQueuePolicy("cancel", reference)
        val record = store.resolve(reference) ?: return null
        return store.cancel(record, reason)?.also { rememberQueue(it, "cancelled") }
    }

    private fun enforceQueuePolicy(operation: String, detail: String = "") {
        val decision = agencyGate.evaluate(atropos.core.policy.LifecycleActionProposals.queue(operation, detail))
        require(decision.disposition == atropos.core.policy.AgencyDisposition.ALLOWED) { decision.reason }
    }

    private fun rememberQueue(record: AgentQueueRecord, title: String) {
        memoryStore.rememberQueue(
            subjectId = record.id,
            title = title,
            body = buildString {
                appendLine("state=${record.state}")
                appendLine("checkpoint=${record.checkpoint}")
                appendLine("attempts=${record.attempts}/${record.maxAttempts}")
                appendLine("job=${record.jobId ?: "none"}")
                appendLine("provider=${record.provider ?: "none"}")
                appendLine("patch=${record.patchId ?: "none"}")
                appendLine("verification=${record.verificationId ?: "none"}")
                appendLine("source=${record.sourceEvidence ?: "unresolved"}")
                appendLine("impacted=${record.impactedSymbols.joinToString(", ").ifBlank { "none" }}")
                appendLine("failure=${record.failureReason ?: "none"}")
            }.trimEnd(),
            tags = listOf("agent", "queue", record.state.name.lowercase())
        )
    }

    private fun rememberQueueSafely(record: AgentQueueRecord, title: String) {
        runCatching { rememberQueue(record, title) }
    }
}
