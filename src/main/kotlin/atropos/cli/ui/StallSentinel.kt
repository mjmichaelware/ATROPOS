/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * A working indicator that cannot lie about working.
 *
 * Every spinner in every tool spins at the same rate whether the process
 * behind it is thinking, blocked on a socket, or dead. That is a small
 * dishonesty most of the time and a costly one here: this operator has left a
 * fourteen-minute run, come back to a cheerfully spinning terminal, and had no
 * way to know it had been hung for ten of those minutes.
 *
 * So the indicator is driven by the engine's own output, not by a timer. While
 * thoughts arrive it animates. When they stop arriving it says how long it has
 * been quiet, and after long enough it says plainly that nothing is coming.
 *
 * The clock is injected, so this is asserted on without a test ever sleeping.
 */
class StallSentinel(
    private val quietAfterMillis: Long = DEFAULT_QUIET_MILLIS,
    private val silentAfterMillis: Long = DEFAULT_SILENT_MILLIS,
    private val now: () -> Long = System::currentTimeMillis
) {

    enum class Liveness {
        /** Output is arriving. The animation is telling the truth. */
        WORKING,

        /** Nothing for a while. Still plausible, but say so. */
        QUIET,

        /** Long enough that the operator should decide whether to intervene. */
        SILENT
    }

    @Volatile
    private var lastOutputAt: Long = now()

    fun observedOutput() {
        lastOutputAt = now()
    }

    fun quietMillis(): Long = (now() - lastOutputAt).coerceAtLeast(0)

    fun liveness(): Liveness = when {
        quietMillis() >= silentAfterMillis -> Liveness.SILENT
        quietMillis() >= quietAfterMillis -> Liveness.QUIET
        else -> Liveness.WORKING
    }

    /**
     * What to show beside the spinner, or empty while output is flowing.
     *
     * Empty during WORKING on purpose: a label that is always present stops
     * being read, and the whole value of this is that its appearance means
     * something.
     */
    fun note(): String {
        val seconds = quietMillis() / 1_000
        return when (liveness()) {
            Liveness.WORKING -> ""
            Liveness.QUIET -> "no output for ${seconds}s"
            Liveness.SILENT ->
                "nothing for ${seconds}s — ctrl+c to stop, or /status to see what it is waiting on"
        }
    }

    private companion object {
        /** Long enough that an ordinary provider call does not trip it. */
        const val DEFAULT_QUIET_MILLIS = 20_000L

        /** Long enough that a slow build does not, but a hang does. */
        const val DEFAULT_SILENT_MILLIS = 90_000L
    }
}
