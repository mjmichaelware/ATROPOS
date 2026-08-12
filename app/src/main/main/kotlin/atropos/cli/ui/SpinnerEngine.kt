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
    private val frames = arrayOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    @Synchronized
    fun start(value: String) {
        require(value.isNotBlank())
        stop()
        message.set(value)

        var frame = 0
        task = executor.scheduleAtFixedRate({
            val current = message.get() ?: return@scheduleAtFixedRate
            renderer("${frames[frame]} $current")
            frame = (frame + 1) % frames.size
        }, 0, 80, TimeUnit.MILLISECONDS)
    }

    fun update(value: String) {
        require(value.isNotBlank())
        if (message.get() != null) message.set(value)
    }

    fun isRunning(): Boolean = message.get() != null

    @Synchronized
    fun stop() {
        message.set(null)
        task?.cancel(false)
        task = null
        renderer(null)
    }

    override fun close() {
        stop()
        executor.shutdownNow()
    }
}
