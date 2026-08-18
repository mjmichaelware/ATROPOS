/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs commands off the input thread, so the composer stays typeable.
 *
 * `router.handleInput()` used to be called directly from the key loop, which
 * meant the loop was inside a self-host run for as long as that run took --
 * minutes -- and read no keys at all. The composer looked alive, drew its
 * border and its caret, and swallowed everything typed into it. Every other
 * agent CLI lets you keep typing while it works, and the reason is not
 * cosmetic: the moment you most want to say "stop" or "actually, also do this"
 * is the moment a long run is underway.
 *
 * ## One command at a time, in order
 *
 * A single worker, not a pool. Commands mutate a shared repository, a shared
 * DAG and a shared goal store; two running at once would interleave writes
 * that every downstream verifier assumes are sequential. So a command typed
 * during a run is *queued*, not raced, and the operator is told where in the
 * line it landed rather than left wondering whether it registered.
 *
 * ## What stays on the input thread
 *
 * Exit. `/exit` has to end the loop, and the loop is blocked reading a key --
 * a worker setting a flag would not be noticed until the operator pressed
 * something else, so quitting would appear to hang. It is instant anyway.
 */
class BackgroundCommandRunner(
    /**
     * How a command is executed.
     *
     * A function rather than the router itself: this class owns *when* work
     * runs, not what the work is, and taking the whole router would make the
     * queueing untestable without constructing a live engine, a config and a
     * provider stack. Production passes `router::handleInput`.
     */
    private val dispatch: (String) -> RouterOutcome,
    private val uiEngine: AnsiTerminalEngine,
    private val onIdle: () -> Unit = {}
) : AutoCloseable {

    private val queue = LinkedBlockingQueue<String>()
    private val running = AtomicReference<String?>(null)
    private val stopped = AtomicBoolean(false)
    private val exited = AtomicBoolean(false)

    private val worker = Thread({ drain() }, "atropos-command").apply {
        isDaemon = true
        start()
    }

    /** What is executing right now, or null when the runner is idle. */
    fun current(): String? = running.get()

    /** How many commands are waiting behind the running one. */
    fun queued(): Int = queue.size

    fun isBusy(): Boolean = running.get() != null

    /** Whether a command asked the session to end. */
    fun hasExited(): Boolean = exited.get()

    /**
     * Accepts a command for execution.
     *
     * Reports the queue position when something is already running. An
     * operator who typed a command into a busy engine and saw nothing would
     * reasonably conclude the keystroke was lost -- which was exactly the old
     * behaviour, and the thing this class exists to end.
     */
    fun submit(text: String) {
        if (stopped.get()) return
        val inFlight = running.get()
        if (inFlight != null) {
            val position = queue.size + 1
            uiEngine.renderNotice(
                "queued (#$position) behind ${summarize(inFlight)} — it will run when that finishes"
            )
        }
        queue.put(text)
    }

    private fun drain() {
        while (!stopped.get()) {
            val next = try {
                queue.take()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }

            running.set(next)
            try {
                if (dispatch(next) == RouterOutcome.EXIT) exited.set(true)
            } catch (failure: Throwable) {
                // The worker must outlive any one command. Before commands ran
                // here at all, a throw unwound into main's catch and ended the
                // session; a background thread dying would be worse still --
                // silent, with every later command queued behind nothing.
                uiEngine.renderError(
                    "command failed (${failure.javaClass.simpleName}): " +
                        (failure.message ?: "unknown failure")
                )
            } finally {
                running.set(null)
                runCatching(onIdle)
            }
        }
    }

    private fun summarize(command: String): String =
        command.lineSequence().first().trim().let {
            if (it.length <= SUMMARY_CELLS) it else it.take(SUMMARY_CELLS - 1) + "…"
        }

    override fun close() {
        stopped.set(true)
        worker.interrupt()
    }

    private companion object {
        const val SUMMARY_CELLS = 32
    }
}
