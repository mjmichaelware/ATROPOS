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
    /** Any SGR sequence starts here. */
    private const val ESCAPE = '\u001B'

    private const val MAX_LABEL = 22

    /**
     * Narrowest value column worth keeping. Below this the hanging indent costs
     * more than it buys, and a phone in portrait would spend most of each line
     * on alignment.
     */
    private const val MIN_VALUE = 8

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

        return lines.flatMap { line ->
            when {
                line.isBlank() -> listOf("")

                // Already coloured by its own renderer, so it is shaped but
                // never repainted.
                //
                // `renderNoticeReactive` runs SemanticLineColorizer first and
                // hands the result here, and painting a string that already
                // carries an SGR sequence trips the compliance guard in
                // TerminalTheme.paint -- an IllegalArgumentException thrown
                // out of frame construction, which the router reported as
                // `/tabs failed (IllegalArgumentException)`. Every notice the
                // colorizer decorated crashed the command that produced it,
                // and the message named ANSI rather than the command, so the
                // failure looked like it belonged to whatever ran last.
                //
                // The same guard SemanticLineColorizer.colorizeLine already
                // applies to its own input, for the same reason.
                line.contains(ESCAPE) -> wrapped(line.trimStart(), inner).map { prefix + it }

                // `header:` becomes the block title, uppercased like the
                // reference's section labels.
                isHeader(line) -> listOf(
                    prefix + theme.paint(
                        Role.BRAND,
                        line.trim().removeSuffix(":").uppercase()
                    )
                )

                else -> {
                    val row = parseRow(line)
                    if (row == null) {
                        wrapped(line.trimStart(), inner).map { prefix + it }
                    } else {
                        val (label, value) = row
                        val gutter = theme.metadata(TerminalText.padEnd(label, labelWidth)) + " "
                        val hanging = " ".repeat(labelWidth + 1)
                        wrapped(value, (inner - labelWidth - 1).coerceAtLeast(MIN_VALUE))
                            .mapIndexed { index, part ->
                                prefix + (if (index == 0) gutter else hanging) + part
                            }
                    }
                }
            }
        }.joinToString("\n")
    }

    /**
     * Breaks a value to fit, rather than cutting it off.
     *
     * This used to call [TerminalText.ellipsize], which was wrong twice over on
     * a phone-width terminal. It threw away the end of every long line — and a
     * lakehouse trace reading `path=… status=MISS reason=…` loses the reason,
     * which is the only part that says what to do next. Worse,
     * [TerminalText.clip] returns ANSI-stripped text, so a line long enough to
     * be cut also lost all of its colour: exactly the dense rows that most
     * needed the semantic palette rendered flat white, while short rows nearby
     * kept theirs.
     *
     * [AnsiLineWrapper] carries the escape sequences across the break, so a
     * wrapped value stays the colour it was.
     */
    private fun wrapped(value: String, width: Int): List<String> {
        if (TerminalText.cellWidth(value) <= width) return listOf(value)

        // Broken at spaces rather than at the exact column. A hard break turns
        // `status=MISS` into `s` + `tatus=MISS` across two lines, which is
        // unreadable in the one place this matters most -- a wall of L3 trace
        // being scanned for the field that changed.
        val lines = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotEmpty()) {
                lines += current.toString()
                current.setLength(0)
            }
        }

        value.split(' ').filter { it.isNotEmpty() }.forEach { chunk ->
            val chunkWidth = TerminalText.cellWidth(chunk)
            when {
                // A path or a hash can exceed the whole column on its own.
                // Nothing can be done for it but a hard break, and losing the
                // tail would be worse than an awkward one.
                chunkWidth > width -> {
                    flush()
                    lines += AnsiLineWrapper.wrap(chunk, width)
                }

                current.isEmpty() -> current.append(chunk)

                TerminalText.cellWidth(current.toString()) + 1 + chunkWidth <= width ->
                    current.append(' ').append(chunk)

                else -> {
                    flush()
                    current.append(chunk)
                }
            }
        }
        flush()
        return lines.ifEmpty { listOf(value) }
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
