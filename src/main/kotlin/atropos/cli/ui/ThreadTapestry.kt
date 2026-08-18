/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import kotlin.math.abs
import kotlin.math.sin

/**
 * The artwork that fills the home screen's empty rows.
 *
 * ATROPOS is the Fate who measures the thread and cuts it, so the home screen
 * draws threads: interfering waves rendered in a density ramp, so the field
 * reads as woven cloth rather than as a chart. It exists because the first
 * screen was two thirds black, and "clean" and "empty" are not the same thing
 * — the first is a choice a viewer can see, the second is a screen nobody
 * finished.
 *
 * ## Why generated rather than a stored image
 *
 * A stored ANSI asset has one size, and this has to be right on a 46-column
 * phone and a 200-column desktop. Generating from the geometry means every
 * terminal gets a composition made for its own dimensions instead of a crop or
 * a stretch, and it costs a few hundred bytes instead of a few hundred
 * kilobytes in a jar that is downloaded over mobile data.
 *
 * ## Why it is deterministic
 *
 * No clock and no random source: the same size always yields the same image.
 * A home screen that shimmered differently on every repaint would pull the eye
 * away from the prompt, which is the one thing on this screen that matters —
 * and an undrawable frame could never be reproduced from a bug report.
 */
class ThreadTapestry(private val theme: TerminalTheme) {

    /**
     * @param phase advances the weave. Left at zero the field is static; the
     *   caller animates by stepping it, which is how the same code serves a
     *   still home screen and a moving one without a second implementation.
     */
    fun render(width: Int, height: Int, phase: Int = 0): List<String> {
        if (width < MINIMUM_CELLS || height <= 0) return emptyList()

        val drift = phase * PHASE_STEP

        return (0 until height).map { row ->
            val line = StringBuilder()
            var previousTier = -1

            for (column in 0 until width) {
                val x = column.toDouble() / width
                val y = row.toDouble() / height.coerceAtLeast(1)

                // Three threads at different frequencies. Where they agree the
                // cloth is dense; where they cancel it opens up.
                val woven =
                    sin(x * 9.0 + drift) +
                        sin((x * 4.0) + (y * 7.0) - drift * 0.6) +
                        sin((y * 11.0) - (x * 2.0) + drift * 0.3)

                val tier = tierOf(abs(woven) / 3.0)

                // Colour is emitted only when it changes. A per-cell escape
                // sequence would make one row of a wide terminal several
                // kilobytes, and the diffing renderer would rewrite all of it
                // on every frame.
                if (tier != previousTier) {
                    if (previousTier >= 0) line.append(theme.reset())
                    line.append(open(tier))
                    previousTier = tier
                }
                line.append(GLYPHS[tier])
            }

            if (previousTier >= 0) line.append(theme.reset())
            line.toString()
        }
    }

    private fun tierOf(intensity: Double): Int =
        when {
            intensity < 0.16 -> 0
            intensity < 0.32 -> 1
            intensity < 0.48 -> 2
            intensity < 0.64 -> 3
            else -> 4
        }

    /** The opening sequence for a tier, or empty when the theme has no colour. */
    private fun open(tier: Int): String {
        val role = if (tier >= 3) Role.BRAND else Role.TEXT_MUTED
        val painted = theme.paint(role, "x")
        return painted.substringBefore('x').takeIf { it.isNotEmpty() && it != painted } ?: ""
    }

    private companion object {
        const val MINIMUM_CELLS = 24
        const val PHASE_STEP = 0.22

        /** Sparse to dense. Space carries the negative space the weave needs. */
        val GLYPHS = charArrayOf(' ', '·', '░', '▒', '▓')
    }
}
