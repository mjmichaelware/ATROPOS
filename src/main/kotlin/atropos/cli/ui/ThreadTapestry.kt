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

        // The cloth is a panel, not a fill.
        //
        // Two things make it one. It is inset, so there is plain background
        // around it -- a field running edge to edge reads as the terminal
        // having been repainted rather than as artwork on the screen, and the
        // eye needs somewhere for the composition to stop. And it is trimmed
        // to close on a thread at every edge, so the lattice finishes instead
        // of being cut off mid-cell. A cropped weave looks like a mistake; a
        // closed one looks chosen.
        val availableWidth = width - MARGIN_COLUMNS * 2
        val availableHeight = height - MARGIN_ROWS * 2
        if (availableWidth < MINIMUM_CELLS || availableHeight < WEFT_ROWS + 1) return emptyList()

        val clothWidth = ((availableWidth - 1) / WARP_COLUMNS) * WARP_COLUMNS + 1
        val clothHeight = ((availableHeight - 1) / WEFT_ROWS) * WEFT_ROWS + 1
        if (clothWidth < MINIMUM_CELLS) return emptyList()

        // Centred in what is left, so the panel sits on the screen rather than
        // hanging off one side of it.
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
     * One row of the lattice.
     *
     * Threads only. An earlier version filled every cell between them from a
     * four-step density ramp, and a later one scattered dots where the light
     * was brightest; both put more information on the screen than a background
     * is allowed to carry, and opening the app meant reading a picture before
     * finding the prompt in front of it. The light lives entirely in the
     * colour now -- the glyphs are a perfectly regular grid, which is what
     * makes it read as formal, and the sheen across it is what stops it
     * reading as graph paper.
     */
    private fun weave(row: Int, width: Int, height: Int, drift: Double): String {
        val line = StringBuilder()
        var previousTier = -1
        val onWeft = row % WEFT_ROWS == 0

        for (column in 0 until width) {
            val onWarp = column % WARP_COLUMNS == 0
            if (!onWarp && !onWeft) {
                // Between the threads there is nothing to draw, so there is
                // nothing to colour. An escape sequence for a blank cell costs
                // bytes and buys no pixels.
                line.append(' ')
                continue
            }

            // A shallow diagonal sheen, carried in colour alone. Smooth and
            // slow: one pass of light across the cloth, not a pattern.
            val sheen = (
                sin((column.toDouble() / width) * 2.2 + (row.toDouble() / height) * 1.6 + drift) + 1.0
                ) / 2.0
            val tier = (sheen * RAMP_RGB.lastIndex).toInt().coerceIn(0, RAMP_RGB.lastIndex)

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
            line.append(if (onWarp && onWeft) CROSS else if (onWarp) WARP else WEFT)
        }

        if (previousTier >= 0) line.append(RESET)
        return line.toString()
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
         * Wide. The first version wove every four columns and three rows, and
         * the first impression of the app was a wall of texture with a prompt
         * somewhere in it. At ten and five the black ground does most of the
         * work and the threads are a frame around it, which is the difference
         * between a background and a picture.
         */
        const val WARP_COLUMNS = 10
        const val WEFT_ROWS = 5

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
