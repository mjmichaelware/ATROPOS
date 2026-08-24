/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.queue

/** A queue entry as a client surface needs it. */
data class QueueEntryView(
    val id: String,
    val task: String,
    val state: String,
    val checkpoint: String,
    val attempts: Int,
    val maxAttempts: Int,
    val terminal: Boolean,
    val failureReason: String?,
    val evidence: String?,
    val createdAt: String,
    val updatedAt: String
)

/** What asking the engine to run work actually did. */
sealed class QueueRunOutcome {
    data class Ran(val entry: QueueEntryView?, val message: String) : QueueRunOutcome()
    data class NothingToRun(val message: String) : QueueRunOutcome()
    data class Unknown(val message: String) : QueueRunOutcome()
    data class Refused(val message: String) : QueueRunOutcome()
}

/**
 * The queue as the bridge is allowed to use it.
 *
 * Narrower than the queue service: a client surface may list, inspect, advance
 * and cancel work. It may not recover leases, reconfigure backpressure or run
 * unbounded batches, and a wider dependency would let it grow those. Keeping
 * the seam here also means the handler is testable without a queue on disk.
 */
interface ConversationWorkRunner {
    fun list(limit: Int): List<QueueEntryView>

    /**
     * Bounded page over the same durable queue projection. Implementations
     * may override this to avoid materialising earlier pages; the default
     * keeps existing test seams source-compatible.
     */
    fun list(limit: Int, offset: Int): List =
        list((limit.coerceAtLeast(0) + offset.coerceAtLeast(0)).coerceAtMost(1_000))
            .drop(offset.coerceAtLeast(0))
            .take(limit.coerceAtLeast(0))

    fun find(id: String): QueueEntryView?

    /** Runs [id], or the next eligible entry when null. */
    fun run(id: String?): QueueRunOutcome

    fun cancel(id: String, reason: String): QueueEntryView?
    fun throttled(): Boolean
}
