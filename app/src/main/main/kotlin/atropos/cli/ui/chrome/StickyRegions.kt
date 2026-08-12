/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

import atropos.cli.ui.design.Breakpoint

/**
 * The row partition of a session screen: pinned header, scrolling transcript,
 * anchored input.
 *
 * This is geometry only. It writes nothing, paints nothing and owns no canvas —
 * `AnsiTerminalEngine` remains the sole canvas owner, and this type exists so
 * the engine can be told *where* the sticky regions are instead of recomputing
 * row offsets from renderer output on every frame.
 *
 * The invariant that matters for HOE-B01 is "no bounce on redraw": for a given
 * terminal size the header and input spans must come out identical every time,
 * and every row gained or lost by a resize must be absorbed by the transcript.
 * That is not a comment — [violations] and [absorbsResizeFrom] make it a
 * predicate a caller (or a test) can actually evaluate, because an invariant
 * that can only be asserted in prose is an invariant nobody checks.
 */
data class StickyRegions(
    val totalRows: Int,
    val columns: Int,
    val header: RowSpan,
    val transcript: RowSpan,
    val input: RowSpan
) {
    /** Reuses the existing responsive vocabulary; chrome invents no widths of its own. */
    val breakpoint: Breakpoint get() = Breakpoint.of(columns)

    /**
     * Everything structurally wrong with this partition, as operator-readable
     * strings. Empty means sound.
     *
     * Returning the list rather than a bare boolean is deliberate: when a wiring
     * change breaks the partition, the caller can say *which* rule broke instead
     * of reporting a nameless layout failure.
     */
    fun violations(): List<String> = buildList {
        if (totalRows <= 0) add("total rows must be positive, was $totalRows")
        if (columns <= 0) add("columns must be positive, was $columns")
        if (header.start != 0) add("header is not pinned to the top row, starts at ${header.start}")
        if (!header.abuts(transcript)) {
            add("transcript does not begin where the header ends (${header.endExclusive} vs ${transcript.start})")
        }
        if (!transcript.abuts(input)) {
            add("input does not begin where the transcript ends (${transcript.endExclusive} vs ${input.start})")
        }
        if (input.endExclusive != totalRows) {
            add("input is not anchored to the bottom row (ends at ${input.endExclusive} of $totalRows)")
        }
        if (transcript.isEmpty) add("transcript has no rows; the operator would see no content")
        if (header.overlaps(transcript) || transcript.overlaps(input) || header.overlaps(input)) {
            add("regions overlap; a row would be painted twice per frame")
        }
        val covered = header.rows + transcript.rows + input.rows
        if (covered != totalRows) add("regions cover $covered rows of $totalRows")
    }

    val isSound: Boolean get() = violations().isEmpty()

    /**
     * The no-bounce predicate, stated across a resize.
     *
     * True when the sticky regions kept their heights, the header stayed at the
     * top, and the transcript took the entire row delta. A `false` here is
     * precisely what the operator perceives as chrome jumping: the header or the
     * input moved for a reason other than the terminal changing size.
     */
    fun absorbsResizeFrom(previous: StickyRegions): Boolean =
        header == previous.header &&
            input.rows == previous.input.rows &&
            transcript.start == previous.transcript.start &&
            transcript.rows - previous.transcript.rows == totalRows - previous.totalRows

    /**
     * True when the chrome is positioned identically to [other] — the redraw
     * case, where the terminal size did not change at all and therefore nothing
     * about the header or input is allowed to move.
     */
    fun chromeMatches(other: StickyRegions): Boolean =
        header == other.header && input == other.input
}
