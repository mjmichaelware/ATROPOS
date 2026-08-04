/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

/**
 * Turns a terminal size plus the chrome's own row appetite into a [StickyRegionPlan].
 *
 * The existing viewport builders derive layout *backwards from renderer output*:
 * they render the composer, count the lines that came back, and subtract. That
 * works, but it makes the header and input positions a function of content, so
 * any content change moves the chrome — the bounce HOE-B01 is about. This solver
 * inverts the dependency. Row counts for the sticky regions are inputs, decided
 * once per size; the transcript is whatever is left. The transcript therefore
 * absorbs every row of resize delta by construction rather than by luck.
 *
 * Pure and total: same arguments, same plan, no clock, no environment, no I/O.
 * That determinism is the whole point — a redraw at an unchanged size cannot
 * produce different geometry, so [StickyRegions.chromeMatches] holds trivially.
 */
object StickyRegionSolver {

    /**
     * A transcript of zero rows is not a layout, it is a hidden conversation.
     * One row is the floor at which the screen still tells the operator something.
     */
    const val MINIMUM_TRANSCRIPT_ROWS = 1

    fun solve(
        totalRows: Int,
        columns: Int,
        headerRows: Int,
        inputRows: Int,
        minimumTranscriptRows: Int = MINIMUM_TRANSCRIPT_ROWS
    ): StickyRegionPlan {
        if (totalRows <= 0 || columns <= 0) {
            return StickyRegionPlan.Refused(
                StickyRegionPlan.Reason.EMPTY_VIEWPORT,
                "viewport is ${columns}x$totalRows; nothing can be placed"
            )
        }
        if (headerRows < 0 || inputRows < 0 || minimumTranscriptRows < 0) {
            return StickyRegionPlan.Refused(
                StickyRegionPlan.Reason.NEGATIVE_REGION_REQUEST,
                "header=$headerRows input=$inputRows minimumTranscript=$minimumTranscriptRows"
            )
        }

        val floor = minimumTranscriptRows.coerceAtLeast(MINIMUM_TRANSCRIPT_ROWS)
        val required = headerRows + inputRows + floor
        if (totalRows < required) {
            return StickyRegionPlan.Refused(
                StickyRegionPlan.Reason.TOO_SHORT_FOR_CHROME,
                "needs $required rows (header $headerRows + input $inputRows + transcript $floor) " +
                    "but the terminal has $totalRows"
            )
        }

        val header = RowSpan(start = 0, rows = headerRows)
        val input = RowSpan(start = totalRows - inputRows, rows = inputRows)
        val transcript = RowSpan.ofBounds(header.endExclusive, input.start)

        return StickyRegionPlan.Resolved(
            StickyRegions(
                totalRows = totalRows,
                columns = columns,
                header = header,
                transcript = transcript,
                input = input
            )
        )
    }

    /**
     * The smallest terminal height this chrome can honestly be drawn in.
     *
     * Exposed so a caller can decide *before* solving whether to ask for the full
     * header at all, instead of discovering the refusal mid-frame.
     */
    fun minimumRows(
        headerRows: Int,
        inputRows: Int,
        minimumTranscriptRows: Int = MINIMUM_TRANSCRIPT_ROWS
    ): Int =
        headerRows.coerceAtLeast(0) +
            inputRows.coerceAtLeast(0) +
            minimumTranscriptRows.coerceAtLeast(MINIMUM_TRANSCRIPT_ROWS)
}
