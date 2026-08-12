package atropos.core.agent

import java.time.Instant

/**
 * The terminal state transitions of an agent job.
 *
 * Two ways a job ends, and both must leave the record complete. Extracted so
 * that "what a finished job looks like" is stated once rather than being
 * implied by two nearly-symmetrical blocks inside the run service.
 *
 * ## Every terminal transition stamps a finish time
 *
 * A job left with a null `finishedAt` looks in-flight forever, which is what
 * makes stale-job recovery unable to tell a crashed run from a running one.
 * Both paths set `finishedAt` and `updatedAt` to the same instant so ordering
 * by either gives the same answer.
 *
 * ## Completion clears the failure reason; failure never clears the result
 *
 * A job that failed, was repaired, and then completed must not keep the earlier
 * failure text — a record carrying both reads as a contradiction. The reverse
 * does not apply: a failing job keeps whatever partial result it produced,
 * because that is the evidence of how far it got.
 */
internal class AgentJobLifecycle(
    private val jobStore: AgentJobStore,
    private val clock: () -> Instant = Instant::now
) {

    fun complete(job: AgentJobRecord): AgentJobRecord {
        // An existing finish time is preserved: a job that recorded when its
        // work actually ended should not be restamped by bookkeeping.
        val finishedAt = job.finishedAt ?: clock()
        return persist(
            job.copy(
                status = AgentJobStatus.COMPLETED,
                finishedAt = finishedAt,
                updatedAt = finishedAt,
                failureReason = null,
                result = job.result ?: DEFAULT_COMPLETION
            )
        )
    }

    fun fail(job: AgentJobRecord, failureReason: String, result: String): AgentJobRecord {
        val finishedAt = clock()
        return persist(
            job.copy(
                status = AgentJobStatus.FAILED,
                finishedAt = finishedAt,
                updatedAt = finishedAt,
                // Never blank: a failed job with no stated reason is
                // indistinguishable from a bug in the failure path itself.
                failureReason = failureReason.trim().ifBlank { DEFAULT_FAILURE },
                result = result.trim().ifBlank { DEFAULT_FAILURE }
            )
        )
    }

    fun persist(record: AgentJobRecord): AgentJobRecord = jobStore.update(record)

    private companion object {
        const val DEFAULT_COMPLETION = "job completed"
        const val DEFAULT_FAILURE = "agent run failed"
    }
}
