/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SpinnerEngine(
    private val renderer: (String?) -> Unit
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "atropos-spinner").apply { isDaemon = true }
    }
    private val message = AtomicReference<String?>(null)
    private var task: ScheduledFuture<*>? = null
    private val thinkingBuffer = AnimatedThinkingBuffer()

    @Synchronized
    fun start(value: String) {
        require(value.isNotBlank())
        stop()
        message.set(value)

        var frame = 0
        task = executor.scheduleAtFixedRate({
            // Guarded, because scheduleAtFixedRate cancels the schedule
            // permanently and silently the first time its task throws. One
            // exception anywhere under renderer() — a layout arithmetic slip,
            // a resize mid-frame — and the spinner drew exactly one frame and
            // then sat there forever. The operator sees a thinking indicator
            // that never moves and no error explaining why, which is the worst
            // of both: it looks alive and it is not.
            val current = message.get() ?: return@scheduleAtFixedRate
            try {
                renderer(thinkingBuffer.render(frame, current))
            } catch (_: Throwable) {
                // Dropped on purpose. A frame that cannot be drawn is not
                // worth ending the animation over, and there is nowhere to
                // report it from here that would not itself be a render.
            }
            frame++
        }, 0, FRAME_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun update(value: String) {
        require(value.isNotBlank())
        if (message.get() != null) message.set(value)
    }

    fun isRunning(): Boolean = message.get() != null

    fun stop() {
        // The clear is deliberately outside the lock. `renderer` reaches back
        // into the terminal engine and takes its monitor, so calling it while
        // holding this one gives two threads two locks in opposite orders —
        // engine-then-spinner from `startSpinner`, spinner-then-engine from
        // here. That inversion is a hang, and a hung UI is indistinguishable
        // from a slow provider.
        synchronized(this) {
            message.set(null)
            task?.cancel(false)
            task = null
        }
        runCatching { renderer(null) }
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }

    private companion object {
        const val FRAME_INTERVAL_MILLIS = 80L
    }
}
