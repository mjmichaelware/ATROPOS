package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.policy.LifecycleActionProposals
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.provider.ProviderOnboardingService
import java.time.Instant

/**
 * Processes queue entries and manages recovery/orchestration.
 *
 * Handles runNext, runMax, resume, and coordinates execution.
 */
internal class AgentQueueProcessor(
    private val config: AtroposConfig,
    private val collector: AgentContextCollector,
    private val store: AgentQueueStore = AgentQueueStore(collector.repoRoot),
    private val recovery: AgentQueueRecovery = AgentQueueRecovery(store),
    private val clock: () -> Instant = { Instant.now() },
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(collector.repoRoot)),
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService()
) {
    private val executor = AgentQueueExecutor(config, collector, onboarding = onboarding, store = store)

    fun runNext(activeProviderName: String): AgentQueueRunResult {
        enforceQueuePolicy("run_next")
        val selected = claimNextEligible()
            ?: return AgentQueueRunResult(null, message = "queue empty or selection lock busy", ran = false)
        if (selected.state == AgentQueueState.FAILED) {
            return AgentQueueRunResult(selected, message = selected.failureReason ?: "queue entry failed before lease", ran = false)
        }
        return executor.executeClaimed(activeProviderName, selected)
    }

    fun runMax(activeProviderName: String, maxCount: Int): AgentQueueBatchResult {
        enforceQueuePolicy("run_max", maxCount.toString())
        if (maxCount !in 1..AgentQueueDefaults.MAX_RUN_COUNT) {
            return AgentQueueBatchResult(
                emptyList(),
                "invalid --max: expected 1 through ${AgentQueueDefaults.MAX_RUN_COUNT}"
            )
        }

        val results = mutableListOf<AgentQueueRunResult>()
        for (index in 0 until maxCount) {
            val result = runNext(activeProviderName)
            results += result
            if (!result.ran || result.queueRecord?.state in setOf(AgentQueueState.FAILED, AgentQueueState.REFUSED)) {
                break
            }
        }
        val ran = results.count { it.ran }
        return AgentQueueBatchResult(results, "queue run processed $ran item(s), limit $maxCount")
    }

    fun resume(activeProviderName: String, reference: String): AgentQueueRunResult {
        enforceQueuePolicy("resume", reference)
        recovery.recover()
        val record = store.resolve(reference)
            ?: return AgentQueueRunResult(null, message = "queue entry not found: $reference", ran = false)
        if (record.state.terminal) {
            return AgentQueueRunResult(record, message = "queue entry is terminal: ${record.state}; resume refused", ran = false)
        }
        val liveLease = record.lease?.takeIf { it.isLive(clock()) }
        if (liveLease != null && record.state in setOf(AgentQueueState.LEASED, AgentQueueState.RUNNING)) {
            return AgentQueueRunResult(record, message = "queue entry has a live lease owned by ${liveLease.owner}", ran = false)
        }
        if (record.checkpoint.ordinal >= AgentQueueCheckpoint.PATCH_APPLIED.ordinal) {
            val reviewed = store.update(
                record.copy(
                    failureReason = "operator review required before resume from checkpoint ${record.checkpoint}"
                ),
                eventType = "resume_refused",
                previousState = record.state,
                message = "operator review required from ${record.checkpoint}"
            )
            return AgentQueueRunResult(reviewed, message = "operator review required from ${record.checkpoint}", ran = false)
        }

        val claimed = claimSpecific(record.id)
            ?: return AgentQueueRunResult(record, message = "unable to acquire lease for resume", ran = false)
        return executor.executeClaimed(activeProviderName, claimed)
    }

    fun recover(): AgentQueueRecoveryResult {
        enforceQueuePolicy("recover")
        return recovery.recover()
    }

    private fun claimNextEligible(): AgentQueueRecord? {
        return store.withSelectionLock {
            recovery.recover()
            val now = clock()
            val candidate = store.allEntries()
                .filter { AgentQueueTransitions.isSelectable(it, now) }
                .sortedBy { it.id }
                .firstOrNull()
                ?: return@withSelectionLock null
            store.acquireLease(candidate.id, store.ownerId(), leaseSeconds()).record
        }
    }

    private fun claimSpecific(queueId: String): AgentQueueRecord? {
        return store.withSelectionLock {
            recovery.recover()
            store.acquireLease(queueId, store.ownerId(), leaseSeconds()).record
        }
    }

    private fun leaseSeconds(): Long {
        val raw = System.getenv("ATROPOS_AGENT_LEASE_SECONDS")?.toLongOrNull()
        return (raw ?: AgentQueueDefaults.DEFAULT_LEASE_SECONDS)
            .coerceIn(AgentQueueDefaults.MIN_LEASE_SECONDS, AgentQueueDefaults.MAX_LEASE_SECONDS)
    }

    private fun enforceQueuePolicy(operation: String, detail: String = "") {
        val decision = agencyGate.evaluate(LifecycleActionProposals.queue(operation, detail))
        require(decision.disposition == AgencyDisposition.ALLOWED) { decision.reason }
    }
}
