/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * Text that is actually bigger, rather than merely brighter.
 *
 * A terminal gives one cell per character, so every heading in this interface
 * is the same physical size as the body text under it and has to signal
 * importance with colour alone. On a phone that fails: at a font size large
 * enough to read comfortably, everything is equally large and the eye has no
 * hierarchy to travel down.
 *
 * Half-block characters give two vertical pixels per cell, so a two-row glyph
 * is genuine large type — four times the area of body text, drawn in the same
 * grid, at no cost in dependencies or fonts.
 *
 * Deliberately small vocabulary: A-Z, 0-9, and a few marks. A renderer that
 * silently dropped unsupported characters would produce headings missing a
 * letter, so anything unknown falls back to the character itself, which is
 * legible and obviously not styled.
 */
object BlockType {

    /** Two rows of half-block glyphs, or the plain text when it will not fit. */
    fun render(text: String, width: Int): List<String> {
        val upper = text.uppercase()
        val needed = upper.length * (GLYPH_CELLS + 1) - 1
        if (needed > width || upper.isEmpty()) return listOf(text)

        return (0 until ROWS).map { row ->
            upper.map { character ->
                GLYPHS[character]?.get(row) ?: character.toString().padEnd(GLYPH_CELLS)
            }.joinToString(" ")
        }
    }

    /** Cells one rendered glyph occupies, so callers can measure before drawing. */
    fun cellsFor(text: String): Int =
        (text.length * (GLYPH_CELLS + 1) - 1).coerceAtLeast(0)

    const val ROWS = 2
    private const val GLYPH_CELLS = 3

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        'A' to listOf("▛▀▜", "▌ ▐"), 'B' to listOf("▛▀▖", "▙▄▟"), 'C' to listOf("▛▀▘", "▙▄▖"),
        'D' to listOf("▛▀▖", "▙▄▟"), 'E' to listOf("▛▀▘", "▙▄▖"), 'F' to listOf("▛▀▘", "▌  "),
        'G' to listOf("▛▀▘", "▙▄▟"), 'H' to listOf("▌ ▐", "▛▀▜"), 'I' to listOf("▀█▀", "▄█▄"),
        'J' to listOf("  ▐", "▙▄▟"), 'K' to listOf("▌ ▞", "▛▀▚"), 'L' to listOf("▌  ", "▙▄▖"),
        'M' to listOf("▛▄▜", "▌ ▐"), 'N' to listOf("▛▖▐", "▌▝▜"), 'O' to listOf("▛▀▜", "▙▄▟"),
        'P' to listOf("▛▀▜", "▌  "), 'Q' to listOf("▛▀▜", "▙▄▚"), 'R' to listOf("▛▀▜", "▛▚ "),
        'S' to listOf("▛▀▘", "▗▄▟"), 'T' to listOf("▀█▀", " █ "), 'U' to listOf("▌ ▐", "▙▄▟"),
        'V' to listOf("▌ ▐", "▝▄▘"), 'W' to listOf("▌ ▐", "▙▀▟"), 'X' to listOf("▚ ▞", "▞ ▚"),
        'Y' to listOf("▚ ▞", " █ "), 'Z' to listOf("▀▀▛", "▙▄▄"),
        '0' to listOf("▛▀▜", "▙▄▟"), '1' to listOf(" █ ", " █ "), '2' to listOf("▀▀▜", "▙▄▄"),
        '3' to listOf("▀▀▜", "▄▄▟"), '4' to listOf("▌ ▐", "▀▀▜"), '5' to listOf("▛▀▘", "▄▄▟"),
        '6' to listOf("▛▀▘", "▙▄▟"), '7' to listOf("▀▀▜", "  ▐"), '8' to listOf("▛▀▜", "▙▄▟"),
        '9' to listOf("▛▀▜", "▄▄▟"),
        ' ' to listOf("   ", "   "), '-' to listOf("   ", "▄▄▄"), '.' to listOf("   ", " ▄ ")
    )
}
