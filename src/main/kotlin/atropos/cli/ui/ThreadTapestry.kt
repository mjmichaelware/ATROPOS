/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
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

            val onWeft = row % WEFT_ROWS == 0

            for (column in 0 until width) {
                val onWarp = column % WARP_COLUMNS == 0

                // A diagonal sheen across the cloth. Smooth and shallow: the
                // lattice is the subject, and a field that swings through the
                // whole ramp would turn the threads into the background.
                val sheen = (
                    sin((column.toDouble() / width) * 3.0 + (row.toDouble() / height) * 2.0 + drift) + 1.0
                    ) / 2.0

                val glyph = when {
                    onWarp && onWeft -> CROSS
                    onWarp -> WARP
                    onWeft -> WEFT
                    else -> FIELD[(sheen * FIELD.size).toInt().coerceIn(0, FIELD.lastIndex)]
                }

                // Threads sit two tiers brighter than the field they cross, so
                // the weave reads as structure lit from one side rather than as
                // a grid drawn over noise.
                val base = (sheen * (RAMP_RGB.size - 2)).toInt().coerceIn(0, RAMP_RGB.size - 2)
                val tier = when {
                    onWarp && onWeft -> RAMP_RGB.lastIndex
                    onWarp || onWeft -> (base + 2).coerceAtMost(RAMP_RGB.lastIndex)
                    else -> base
                }

                // Colour is emitted only when it changes. A per-cell escape
                // sequence would make one row of a wide terminal several
                // kilobytes, and the diffing renderer would rewrite all of it
                // on every frame.
                if (tier != previousTier) {
                    // No RESET first: setting a foreground colour replaces the
                    // previous one outright, so emitting both doubled every
                    // row's escape count for nothing. A 120-cell row was
                    // carrying 126 escape sequences -- more than one per cell,
                    // which is the exact waste this batching exists to avoid.
                    line.append(open(tier))
                    previousTier = tier
                }
                line.append(glyph)
            }

            if (previousTier >= 0) line.append(RESET)
            line.toString()
        }
    }

    /**
     * The 24-bit colour for a tier, or empty when the terminal has none.
     *
     * Truecolor rather than the theme's semantic roles. Roles exist to say
     * what a thing *means* — an error, a path, a heading — and this field
     * means nothing; it is texture. Borrowing `BRAND` for it would make the
     * artwork change colour whenever the brand did, and would put the same ink
     * on the wordmark and on the wallpaper behind it.
     *
     * The ramp runs deep indigo → violet → periwinkle → near-white, so the
     * densest threads read as lit and the sparse ones recede. Blue and white
     * against the purple wordmark rather than more purple: a field in the
     * brand colour competes with the brand mark, where a cooler field sits
     * behind it.
     */
    private fun open(tier: Int): String {
        if (!theme.colorEnabled) return ""
        val (r, g, b) = RAMP_RGB[tier.coerceIn(0, RAMP_RGB.lastIndex)]
        return "\u001B[38;2;$r;$g;${b}m"
    }

    private companion object {
        const val MINIMUM_CELLS = 24
        const val RESET = "\u001B[0m"
        const val PHASE_STEP = 0.22

        /** Warp runs down, weft runs across, and they cross. */
        const val WARP = '\u2502'
        const val WEFT = '\u2500'
        const val CROSS = '\u253C'

        /** Threads every four columns and every three rows: dense enough to
         *  read as cloth, open enough that the field shows through. */
        const val WARP_COLUMNS = 4
        const val WEFT_ROWS = 3

        /** The cloth between the threads, sparse to dense. */
        val FIELD = charArrayOf(' ', '\u00b7', '\u2591', '\u2592')

        /**
         * Deep indigo through periwinkle to near-white.
         *
         * Cool on purpose: the wordmark is purple, and a field in the same
         * hue competes with the mark instead of sitting behind it.
         */
        val RAMP_RGB = listOf(
            Triple(0x14, 0x18, 0x30),
            Triple(0x24, 0x2B, 0x5C),
            Triple(0x3A, 0x44, 0x8E),
            Triple(0x56, 0x63, 0xB8),
            Triple(0x7E, 0x8F, 0xD6),
            Triple(0xA8, 0xB8, 0xEC),
            Triple(0xDA, 0xE3, 0xFB)
        )
    }
}
