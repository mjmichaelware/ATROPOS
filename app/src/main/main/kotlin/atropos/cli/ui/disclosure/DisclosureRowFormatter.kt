/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import atropos.cli.ui.TerminalText

/**
 * Turns one [DisclosureRow] into plain text lines: the `▸ Label` header and,
 * when the row is open, the content revealed so far.
 *
 * Pure and colourless on purpose. It emits no ANSI, reads no environment, and
 * makes no state decisions — whether an expand is available comes in already
 * decided via [DisclosureRow.canExpand], and the terminal facts come in via
 * [DisclosureRowStyle]. That split is what lets the disclosure state machine be
 * tested with no terminal and this formatter be tested with no state machine,
 * and it keeps colour out of strings that a caller may want to measure or clip
 * afterwards.
 *
 * Two failure modes it is written against:
 *
 *  - **Marker-only state.** The row never signals open/closed by glyph alone.
 *    The label is always present and the level is spelled in words when there
 *    is room, so a terminal that mangles `▸` and `▾` still tells the operator
 *    where they are — the same redundancy rule `RunState` enforces for status.
 *  - **Unbounded content.** Revealed lines are clipped to the available width
 *    with an ASCII-safe ellipsis rather than being allowed to wrap
 *    unpredictably, because a 400-column tool payload wrapping inside a
 *    transcript destroys the surrounding layout.
 */
object DisclosureRowFormatter {

    /** Cells between the marker glyph and the label. */
    private const val MARKER_PADDING: Int = 1

    /** Indent applied to revealed lines, relative to the header. */
    private const val CONTENT_INDENT: Int = 2

    /**
     * The header line alone: marker, label, and — width permitting — the summary
     * and the current level.
     *
     * A collapsed row is exactly this one line. That is the whole of HOE-B02's
     * default state, and it is why the summary lives on the header rather than
     * inside L1: a reader who never expands still learns what the row is about.
     */
    fun header(row: DisclosureRow, style: DisclosureRowStyle = DisclosureRowStyle.DEFAULT): String {
        val marker = DisclosureMarker.of(row.state, row.canExpand).resolve(style.asciiOnly)
        val pad = " ".repeat(MARKER_PADDING)
        val prefix = " ".repeat(style.indent) + marker + pad + row.label

        val suffix = suffix(row, style)
        if (suffix.isEmpty()) return prefix

        val room = style.contentWidth - TerminalText.cellWidth(marker + pad + row.label) - 1
        if (room < MIN_SUFFIX_ROOM) return prefix
        return prefix + " " + clip(suffix, room, style)
    }

    /**
     * The revealed lines below the header, indented, clipped, in reveal order.
     *
     * Empty for a collapsed row. Callers appending to a stream should use
     * [addedLines] with the [DisclosureReveal] instead, so an expand does not
     * re-emit what is already on screen.
     */
    fun body(row: DisclosureRow, style: DisclosureRowStyle = DisclosureRowStyle.DEFAULT): List<String> =
        indentAndClip(row.visibleLines(), style)

    /**
     * Just the lines an expand step added.
     *
     * This is the streaming counterpart to [body]. Formatting the reveal's
     * [DisclosureReveal.added] rather than recomputing a view is what makes
     * "reveals only additional detail" true of the bytes written to the
     * terminal, not merely of the model behind them.
     */
    fun addedLines(
        reveal: DisclosureReveal,
        style: DisclosureRowStyle = DisclosureRowStyle.DEFAULT
    ): List<String> = indentAndClip(reveal.added, style)

    /** Header plus body — the full repaint of one row. */
    fun render(row: DisclosureRow, style: DisclosureRowStyle = DisclosureRowStyle.DEFAULT): List<String> =
        listOf(header(row, style)) + body(row, style)

    /**
     * Orientation text after the label: the summary, and the level when the
     * terminal is wide enough to spare the cells.
     */
    private fun suffix(row: DisclosureRow, style: DisclosureRowStyle): String {
        val level = row.state.revealed
        val depth = if (style.showsLevelSuffix && level != null) {
            val deepest = row.content.deepest
            if (deepest != null && deepest != level) {
                "${level.label} of ${deepest.label}"
            } else {
                level.label
            }
        } else {
            ""
        }

        val summary = row.content.summary
        return when {
            summary.isNotEmpty() && depth.isNotEmpty() -> "$summary  $depth"
            summary.isNotEmpty() -> summary
            else -> depth
        }
    }

    private fun indentAndClip(lines: List<String>, style: DisclosureRowStyle): List<String> {
        if (lines.isEmpty()) return emptyList()
        val pad = " ".repeat(style.indent + CONTENT_INDENT)
        val room = (style.contentWidth - CONTENT_INDENT).coerceAtLeast(DisclosureRowStyle.MINIMUM_CONTENT)
        return lines.map { pad + clip(TerminalText.sanitize(it), room, style) }
    }

    /**
     * Width-safe truncation with an ASCII-safe mark.
     *
     * `TerminalText.ellipsize` is not used here because it hardcodes `…`, which
     * is exactly the character an ASCII-only terminal cannot show.
     */
    private fun clip(value: String, room: Int, style: DisclosureRowStyle): String {
        if (TerminalText.cellWidth(value) <= room) return value
        val mark = style.ellipsis
        val keep = room - TerminalText.cellWidth(mark)
        if (keep <= 0) return TerminalText.clip(value, room)
        return TerminalText.clip(value, keep) + mark
    }

    /** Below this, a suffix is all ellipsis and says nothing; drop it instead. */
    private const val MIN_SUFFIX_ROOM: Int = 8
}
