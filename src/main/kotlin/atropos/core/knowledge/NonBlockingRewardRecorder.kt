/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.knowledge

import atropos.core.verification.RewardEvent
import atropos.core.verification.RewardRecorder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Bounded asynchronous boundary for the canonical reward recorder.
 *
 * Durable format and atomic replacement remain owned by [AtomicRewardRecorder].
 * This adapter only prevents verification from blocking on filesystem I/O;
 * a full queue is refused instead of silently dropping an event.
 */
class NonBlockingRewardRecorder(
    private val delegate: RewardRecorder,
    queueCapacity: Int = 64
) : RewardRecorder, AutoCloseable {
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(queueCapacity.coerceAtLeast(1)),
        { runnable -> Thread(runnable, "atropos-reward-writer").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    override fun record(event: RewardEvent) {
        try {
            executor.execute { delegate.record(event) }
        } catch (failure: RejectedExecutionException) {
            throw IllegalStateException("reward persistence queue is full", failure)
        }
    }

    override fun close() {
        executor.shutdown()
    }
}
