package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.memory.LocalMemoryStore
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.LifecycleActionProposals
import atropos.core.policy.AgencyDisposition
import java.time.Instant

data class AgentQueueRunResult(
    val queueRecord: AgentQueueRecord?,
    val jobRecord: AgentJobRecord? = null,
    val message: String,
    val ran: Boolean
)

data class AgentQueueBatchResult(
    val results: List<AgentQueueRunResult>,
    val message: String
) {
    fun render(): String = buildString {
        appendLine(message)
        results.forEach { result ->
            val record = result.queueRecord
            appendLine(
                "${record?.id ?: "none"} state=${record?.state ?: "none"} " +
                    "job=${record?.jobId ?: "none"} ran=${result.ran} ${result.message}"
            )
        }
    }.trimEnd()
}

class AgentQueueService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val store: AgentQueueStore = AgentQueueStore(collector.repoRoot),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(collector.repoRoot)),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(collector.repoRoot.resolve(".atropos/memory").toFile())
) {
    private val manager = AgentQueueManager(config, collector)
    private val processor = AgentQueueProcessor(config, collector)
    private val backpressure = AgentQueueBackpressure(store, AgentQueueRecovery(store))

    fun enqueue(task: String, smokeCommand: String? = null): AgentQueueRecord =
        manager.enqueue(task, smokeCommand)

    fun enqueueUnavailable(task: String, retryAtEpochMs: Long? = null): AgentQueueRecord? =
        manager.enqueueUnavailable(task, retryAtEpochMs)

    fun list(limit: Int = 20): List<AgentQueueRecord> = manager.list(limit)

    fun resolve(reference: String): AgentQueueRecord? = manager.resolve(reference)

    fun latest(): AgentQueueRecord? = manager.latest()

    fun runNext(activeProviderName: String): AgentQueueRunResult = processor.runNext(activeProviderName)

    fun runMax(activeProviderName: String, maxCount: Int): AgentQueueBatchResult =
        processor.runMax(activeProviderName, maxCount)

    fun resume(activeProviderName: String, reference: String): AgentQueueRunResult =
        processor.resume(activeProviderName, reference)

    fun cancel(reference: String, reason: String = "operator cancelled queue entry"): AgentQueueRecord? =
        manager.cancel(reference, reason)

    fun recover(): AgentQueueRecoveryResult = processor.recover()

    fun storageSummary(): String = buildString {
        appendLine("root: ${store.queueRoot()}")
        appendLine("entries: ${store.entriesDirectory()}/<queue-id>.meta")
        appendLine("events: ${store.eventsDirectory()}/<queue-id>.events")
        appendLine("selection lock: ${store.locksDirectory()}/queue.lock")
    }.trimEnd()

    fun shouldThrottle(): Boolean = backpressure.shouldThrottle()

    fun computeBackpressure(): Long = backpressure.computeBackpressure()

    fun boundedExecutor(): BoundedWorkExecutor = BoundedWorkExecutor(this, agencyGate)

    private fun enforceQueuePolicy(operation: String, detail: String = "") {
        val decision = agencyGate.evaluate(LifecycleActionProposals.queue(operation, detail))
        require(decision.disposition == AgencyDisposition.ALLOWED) { decision.reason }
    }
}
