package atropos.core.agent

import java.time.Instant

/**
 * Manages backpressure, throttling, and lease management for queue processing.
 *
 * Determines selection priority, computes lease durations, and handles backoff
 * calculations for retry scenarios.
 */
internal class AgentQueueBackpressure(
    private val store: AgentQueueStore,
    private val recovery: AgentQueueRecovery,
    private val clock: () -> Instant = { Instant.now() }
) {
    /**
     * Computes the number of seconds for a lease based on configuration.
     *
     * Respects environment variable ATROPOS_AGENT_LEASE_SECONDS and enforces
     * min/max bounds from [AgentQueueDefaults].
     */
    fun leaseSeconds(): Long {
        val raw = System.getenv("ATROPOS_AGENT_LEASE_SECONDS")?.toLongOrNull()
        return (raw ?: AgentQueueDefaults.DEFAULT_LEASE_SECONDS)
            .coerceIn(AgentQueueDefaults.MIN_LEASE_SECONDS, AgentQueueDefaults.MAX_LEASE_SECONDS)
    }

    /**
     * Computes extended lease for operations that need longer durations.
     *
     * Applies a minimum of 30 minutes and maximum from defaults.
     */
    fun operationLeaseSeconds(): Long =
        leaseSeconds().coerceAtLeast(30L * 60L).coerceAtMost(AgentQueueDefaults.MAX_LEASE_SECONDS)

    /**
     * Computes backoff delay for retry attempts.
     *
     * Exponential backoff: 5 seconds per attempt, capped at 60 seconds.
     */
    fun backoffSeconds(attempts: Int): Long =
        (5L * attempts.coerceAtLeast(1)).coerceAtMost(60L)

    /**
     * Determines whether the queue service should throttle (rate limit) operations.
     *
     * Checks the number of live leases to prevent overload when many queue
     * entries are actively being processed.
     */
    fun shouldThrottle(): Boolean {
        val liveLeases = store.allEntries()
            .count { it.lease?.isLive(clock()) == true }
        return liveLeases >= AgentQueueDefaults.MAX_CONCURRENT_LEASES
    }

    /**
     * Computes total backpressure based on queue state.
     *
     * Returns a delay in seconds that should be imposed before processing,
     * considering both throttle state and entry count.
     */
    fun computeBackpressure(): Long {
        if (shouldThrottle()) return 5L
        val count = store.allEntries().size
        return when {
            count > 100 -> 2L
            count > 50 -> 1L
            else -> 0L
        }
    }

    /**
     * Selects the next eligible queue entry with proper backpressure handling.
     *
     * Returns null if queue is empty or throttled, otherwise returns the
     * next entry that should be processed.
     */
    fun claimNextEligible(): AgentQueueRecord? {
        return store.withSelectionLock {
            recovery.recover()
            if (shouldThrottle()) return@withSelectionLock null
            val now = clock()
            val candidate = store.allEntries()
                .filter { AgentQueueTransitions.isSelectable(it, now) }
                .sortedBy { it.id }
                .firstOrNull()
                ?: return@withSelectionLock null
            store.acquireLease(candidate.id, store.ownerId(), leaseSeconds()).record
        }
    }

    /**
     * Acquires a specific queue entry for resumption.
     *
     * Handles lease acquisition with proper locking and recovery.
     */
    fun claimSpecific(queueId: String): AgentQueueRecord? {
        return store.withSelectionLock {
            recovery.recover()
            store.acquireLease(queueId, store.ownerId(), leaseSeconds()).record
        }
    }
}
