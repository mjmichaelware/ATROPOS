/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * The run, as a thread being measured and cut.
 *
 * Atropos is the Fate who measures the thread and cuts it. Every other surface
 * in this program borrows that image; this one makes it do a job. The thread
 * grows with work actually finished — nodes done against nodes total — and the
 * shears close on it when the run ends.
 *
 * A spinner says "something is happening". This says *how much* is happening,
 * out of how much, using the one image the whole product is named for. The
 * distinction matters on a phone, where an operator watching a fourteen-minute
 * run has no other way to tell a slow stage from a stuck one.
 *
 * Nothing here animates on a timer. The bar is a function of counts, so a run
 * that stops advancing shows a thread that stops growing — a UI that cannot
 * lie about being alive.
 */
class ThreadProgress(private val theme: TerminalTheme) {

    /**
     * @param done work finished. @param total work known. A total of zero
     *   means the amount is not known yet, which is a different thing from
     *   nothing to do, and is drawn as an unmeasured thread rather than as an
     *   empty bar that would read as no progress.
     */
    fun render(done: Int, total: Int, width: Int, cut: Boolean = false): String {
        val room = width.coerceAtLeast(MINIMUM_CELLS)
        val label = if (total > 0) "$done/$total" else "measuring"
        val track = (room - label.length - LABEL_GAP - SHEARS_CELLS).coerceAtLeast(4)

        if (total <= 0) {
            return theme.paint(Role.ACCENT_FOCUS, UNMEASURED.repeat(track)) +
                " ".repeat(LABEL_GAP) + theme.subdued(label)
        }

        val ratio = (done.toDouble() / total).coerceIn(0.0, 1.0)
        val drawn = (ratio * track).toInt().coerceIn(0, track)
        val spun = theme.paint(if (cut) Role.STATUS_VERIFIED else Role.ACCENT_FOCUS, THREAD.repeat(drawn))
        val unspun = theme.subdued(SLACK.repeat(track - drawn))
        val shears = if (cut) theme.paint(Role.STATUS_VERIFIED, CUT) else theme.subdued(SHEARS)

        return spun + unspun + shears + " ".repeat(LABEL_GAP) +
            theme.paint(if (cut) Role.STATUS_VERIFIED else Role.TEXT_SECONDARY, label)
    }

    private companion object {
        /** Below this there is no room for a thread and a count both. */
        const val MINIMUM_CELLS = 16

        /** Spun thread, and the slack still to be drawn through. */
        const val THREAD = "━"
        const val SLACK = "┄"

        /** Not knowing how much there is to do is its own state. */
        const val UNMEASURED = "┄"

        /** The shears, open while the run continues and closed when it ends. */
        const val SHEARS = "✂"
        const val CUT = "✂"
        const val SHEARS_CELLS = 1
        const val LABEL_GAP = 2
    }
}
