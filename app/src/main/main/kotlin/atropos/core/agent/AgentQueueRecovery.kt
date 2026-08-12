package atropos.core.agent

import java.time.Instant

data class AgentQueueRecoveryTransition(
    val queueId: String,
    val previousState: AgentQueueState,
    val newState: AgentQueueState,
    val checkpoint: AgentQueueCheckpoint,
    val message: String
)

data class AgentQueueRecoveryResult(
    val transitions: List<AgentQueueRecoveryTransition>,
    val inspected: Int
) {
    fun render(): String = buildString {
        appendLine("queue recovery inspected: $inspected")
        if (transitions.isEmpty()) {
            appendLine("no stale leases or abandoned entries found")
        } else {
            transitions.forEach { transition ->
                appendLine(
                    "${transition.queueId}: ${transition.previousState} -> ${transition.newState} " +
                        "checkpoint=${transition.checkpoint} ${transition.message}"
                )
            }
        }
    }.trimEnd()
}

class AgentQueueRecovery(
    private val store: AgentQueueStore,
    private val clock: () -> Instant = { Instant.now() }
) {
    fun recover(): AgentQueueRecoveryResult {
        val transitions = mutableListOf<AgentQueueRecoveryTransition>()
        val entries = store.allEntries()
        val now = clock()

        entries.forEach { record ->
            val transition = recoverOne(record, now) ?: return@forEach
            transitions += transition
        }

        return AgentQueueRecoveryResult(transitions = transitions, inspected = entries.size)
    }

    private fun recoverOne(record: AgentQueueRecord, now: Instant): AgentQueueRecoveryTransition? {
        if (record.state.terminal) return null
        if (record.state !in setOf(AgentQueueState.LEASED, AgentQueueState.RUNNING)) return null
        val lease = record.lease
        if (lease != null && lease.isLive(now)) return null

        val nextState: AgentQueueState
        val message: String
        val nextEligibleAt: Instant?
        val finishedAt: Instant?
        val cancellationRequested = record.cancellationRequested

        when {
            cancellationRequested -> {
                nextState = AgentQueueState.CANCELLED
                message = "expired lease recovered into cancellation"
                nextEligibleAt = null
                finishedAt = now
            }
            record.attempts >= record.maxAttempts -> {
                nextState = AgentQueueState.FAILED
                message = "expired lease exhausted retry policy"
                nextEligibleAt = null
                finishedAt = now
            }
            record.state == AgentQueueState.LEASED -> {
                nextState = AgentQueueState.QUEUED
                message = "expired claim lease cleared before work started"
                nextEligibleAt = null
                finishedAt = null
            }
            else -> {
                nextState = AgentQueueState.RETRY_WAIT
                message = "expired lease cleared; eligible after bounded backoff"
                nextEligibleAt = now.plusSeconds(backoffSeconds(record.recoveryCount + 1))
                finishedAt = null
            }
        }

        val updated = store.update(
            record.copy(
                state = nextState,
                lease = null,
                nextEligibleAt = nextEligibleAt,
                recoveryCount = record.recoveryCount + 1,
                failureReason = if (nextState == AgentQueueState.FAILED) message else record.failureReason,
                cancelledAt = if (nextState == AgentQueueState.CANCELLED) now else record.cancelledAt,
                finishedAt = finishedAt
            ),
            eventType = "recovered",
            previousState = record.state,
            message = message
        )

        return AgentQueueRecoveryTransition(
            queueId = updated.id,
            previousState = record.state,
            newState = updated.state,
            checkpoint = updated.checkpoint,
            message = message
        )
    }

    private fun backoffSeconds(recoveryCount: Int): Long =
        (5L * recoveryCount.coerceAtLeast(1)).coerceAtMost(60L)
}
