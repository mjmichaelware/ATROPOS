/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import kotlin.math.sin

/**
 * The artwork that fills the home screen's empty rows.
 *
 * ATROPOS is the Fate who measures the thread and cuts it, so the home screen
 * draws threads: an open lattice of warp and weft, lit by a shallow diagonal
 * sheen. It exists because the first screen was two thirds black, and "clean"
 * and "empty" are not the same thing — the first is a choice a viewer can see,
 * the second is a screen nobody finished.
 *
 * ## Why it is quiet
 *
 * It is a background. It sits behind the one thing on this screen that
 * matters, which is the prompt, and every mark it adds is a mark the eye has
 * to rule out before it finds that prompt. So: an open weave rather than a
 * tight one, an empty field rather than a density ramp, a narrow range of cool
 * colour rather than the full ladder to white, and a margin of plain
 * background around the whole thing so it reads as a piece of artwork on the
 * screen rather than as the screen having been repainted.
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

        val availableWidth = width - MARGIN_COLUMNS * 2
        val availableHeight = height - MARGIN_ROWS * 2
        if (availableWidth < MINIMUM_CELLS || availableHeight < WEFT_ROWS + 1) return emptyList()

        val clothWidth = ((availableWidth - 1) / WARP_COLUMNS) * WARP_COLUMNS + 1
        val clothHeight = ((availableHeight - 1) / WEFT_ROWS) * WEFT_ROWS + 1
        if (clothWidth < MINIMUM_CELLS) return emptyList()

        val left = MARGIN_COLUMNS + (availableWidth - clothWidth) / 2
        val top = MARGIN_ROWS + (availableHeight - clothHeight) / 2
        val indent = " ".repeat(left)
        val drift = phase * PHASE_STEP

        return (0 until height).map { row ->
            val clothRow = row - top
            if (clothRow < 0 || clothRow >= clothHeight) ""
            else indent + weave(clothRow, clothWidth, clothHeight, drift)
        }
    }

    /**
     * One row of the panel.
     *
     * A selvedge, then the lattice inside it.
     *
     * The bare lattice was formal and it was scaffolding: an open grid with
     * nothing to say where it began or ended, so it read as the terminal
     * having drawn guide lines rather than as an object placed on the screen.
     * A finished edge is what makes cloth a piece of cloth. It costs one ring
     * of glyphs and adds no interior noise whatsoever, which is the only kind
     * of decoration a background is allowed.
     *
     * Everything else is carried in colour: the selvedge sits at the top of
     * the ramp, crossings a tier below it, threads dimmer still, and the whole
     * panel fades from lit at the top to shadow at the bottom, the way hanging
     * fabric does. Depth without a single extra mark.
     */
    private fun weave(row: Int, width: Int, height: Int, drift: Double): String {
        val line = StringBuilder()
        var previousTier = -1
        val onWeft = row % WEFT_ROWS == 0
        val onEdgeRow = row == 0 || row == height - 1

        // Lit from above. The gradient runs down the panel rather than across
        // it because a light source overhead is what the eye expects, and a
        // horizontal ramp reads as a highlight sliding sideways.
        val fall = 1.0 - (row.toDouble() / (height - 1).coerceAtLeast(1))
        val sheen = (sin(fall * 1.4 + drift) + 1.0) / 2.0

        for (column in 0 until width) {
            val onWarp = column % WARP_COLUMNS == 0
            val onEdgeColumn = column == 0 || column == width - 1

            val glyph = when {
                onEdgeRow && onEdgeColumn -> corner(row == 0, column == 0)
                onEdgeRow -> SELVEDGE_HORIZONTAL
                onEdgeColumn -> SELVEDGE_VERTICAL
                onWarp && onWeft -> CROSS
                onWarp -> WARP
                onWeft -> WEFT
                else -> ' '
            }
            if (glyph == ' ') {
                line.append(' ')
                continue
            }

            val base = (sheen * (RAMP_RGB.size - 3)).toInt().coerceIn(0, RAMP_RGB.size - 3)
            val tier = when {
                onEdgeRow || onEdgeColumn -> RAMP_RGB.lastIndex
                onWarp && onWeft -> (base + 2).coerceAtMost(RAMP_RGB.lastIndex)
                else -> base
            }

            if (tier != previousTier) {
                line.append(open(tier))
                previousTier = tier
            }
            line.append(glyph)
        }

        if (previousTier >= 0) line.append(RESET)
        return line.toString()
    }

    private fun corner(top: Boolean, left: Boolean): Char = when {
        top && left -> '\u250F'
        top -> '\u2513'
        left -> '\u2517'
        else -> '\u251B'
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

        /**
         * How far apart the threads sit.
         *
         * Six and three. Ten and five was open to the point of emptiness --
         * four enormous cells filling the lower half of a phone, which reads
         * as an unfinished layout rather than as a considered one. This pitch
         * is close enough to be cloth and open enough to stay quiet.
         */
        const val WARP_COLUMNS = 6
        const val WEFT_ROWS = 3

        /** The finished edge. Heavier than the threads it contains. */
        const val SELVEDGE_HORIZONTAL = '\u2501'
        const val SELVEDGE_VERTICAL = '\u2503'

        /** A blank margin either side, and a blank row above and below. */
        const val MARGIN_COLUMNS = 2
        const val MARGIN_ROWS = 1



        /**
         * Deep indigo through periwinkle to near-white.
         *
         * Cool on purpose: the wordmark is purple, and a field in the same
         * hue competes with the mark instead of sitting behind it.
         */
        val RAMP_RGB = listOf(
            Triple(0x12, 0x16, 0x28),
            Triple(0x1C, 0x22, 0x44),
            Triple(0x2A, 0x33, 0x66),
            Triple(0x3B, 0x46, 0x8C),
            Triple(0x50, 0x5E, 0xAE),
            Triple(0x6C, 0x7C, 0xC8),
            Triple(0x93, 0xA4, 0xE0)
        )
    }
}
