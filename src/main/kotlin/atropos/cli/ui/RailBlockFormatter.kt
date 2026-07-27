/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/**
 * Re-renders the legacy `header:` + indented `key: value` output that most
 * `Status*Renderer` classes still emit into the pinned reference's rail block
 * layout, without rewriting each renderer.
 *
 * The reference draws no boxes: a block is a coloured left rail, two columns of
 * padding, then aligned content. This formatter is the bridge that lets the
 * renderers still producing flat text inherit that layout, so the whole product
 * reads as one surface rather than a mix of two eras.
 *
 * It is deliberately a *presentation* transform only — it parses nothing
 * semantic, invents no values, and passes through anything it does not
 * recognise unchanged, so a renderer's meaning can never be altered by
 * formatting.
 */
object RailBlockFormatter {

    /** Widest label seen in the reference's aligned rows before it wraps. */
    private const val MAX_LABEL = 22

    fun format(raw: String, theme: TerminalTheme, width: Int = 80): String {
        if (raw.isBlank()) return raw

        val railGlyph = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
        val rail = theme.paint(Role.ACCENT_FOCUS, railGlyph)
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val prefix = rail + pad
        val inner = (width - railGlyph.length - Glyphs.RAIL_PADDING).coerceAtLeast(12)

        val lines = raw.trimEnd().lines()

        // Label column: widest `key:` among indented rows, so the block aligns
        // the way every rail block in the product aligns.
        val labelWidth = lines
            .mapNotNull { parseRow(it)?.first?.length }
            .maxOrNull()
            ?.coerceAtMost(MAX_LABEL)
            ?: 0

        return lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> ""

                // `header:` becomes the block title, uppercased like the
                // reference's section labels.
                isHeader(line) -> prefix + theme.paint(
                    Role.BRAND,
                    line.trim().removeSuffix(":").uppercase()
                )

                else -> {
                    val row = parseRow(line)
                    if (row == null) {
                        prefix + TerminalText.ellipsize(line.trimStart(), inner)
                    } else {
                        val (label, value) = row
                        prefix +
                            theme.metadata(TerminalText.padEnd(label, labelWidth)) +
                            " " +
                            TerminalText.ellipsize(value, (inner - labelWidth - 1).coerceAtLeast(4))
                    }
                }
            }
        }
    }

    /** A top-level `something:` line with no value after the colon. */
    private fun isHeader(line: String): Boolean =
        !line.startsWith(" ") &&
            line.trimEnd().endsWith(":") &&
            line.trimEnd().dropLast(1).isNotBlank()

    /** An indented `  key: value` row. Returns null for anything else. */
    private fun parseRow(line: String): Pair<String, String>? {
        if (!line.startsWith(" ")) return null
        val trimmed = line.trim()
        val colon = trimmed.indexOf(':')
        if (colon <= 0 || colon == trimmed.lastIndex) return null
        val label = trimmed.substring(0, colon).trim()
        val value = trimmed.substring(colon + 1).trim()
        if (label.isEmpty() || value.isEmpty()) return null
        if (label.length > MAX_LABEL) return null
        return label to value
    }

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()
}
