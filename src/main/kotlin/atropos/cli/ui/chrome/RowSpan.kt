/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

/**
 * A half-open run of terminal rows, `[start, start + rows)`.
 *
 * The sticky-chrome arithmetic in [StickyRegionSolver] used to live as bare
 * `Int` subtraction inside the viewport builders, where `separatorRow - 1` and
 * `footerRow - height` could silently go negative and then be clamped by
 * whichever `coerceAtLeast` happened to be nearest. Clamped-away negatives are
 * exactly how a redraw ends up one row off from the previous frame, which the
 * operator sees as the header and input bouncing.
 *
 * Making the span a value type with a construction-time guard means a negative
 * height cannot enter the model at all: the solver must refuse before it builds
 * one. Half-open is deliberate — [endExclusive] composes with Kotlin ranges and
 * removes the off-by-one that `lastRow` arithmetic invites.
 */
data class RowSpan(val start: Int, val rows: Int) {
    init {
        require(start >= 0) { "row span start must be non-negative, was $start" }
        require(rows >= 0) { "row span height must be non-negative, was $rows" }
    }

    /** First row *after* this span. Equal to [start] when the span is empty. */
    val endExclusive: Int get() = start + rows

    /** Last row inside this span, or `null` when the span holds no rows. */
    val lastRow: Int? get() = if (rows == 0) null else endExclusive - 1

    val isEmpty: Boolean get() = rows == 0

    operator fun contains(row: Int): Boolean = row >= start && row < endExclusive

    /** True when the two spans share at least one row. Empty spans never overlap. */
    fun overlaps(other: RowSpan): Boolean =
        !isEmpty && !other.isEmpty && start < other.endExclusive && other.start < endExclusive

    /** True when [other] begins on the row immediately after this span ends. */
    fun abuts(other: RowSpan): Boolean = endExclusive == other.start

    companion object {
        /**
         * Builds a span from inclusive/exclusive bounds.
         *
         * Refuses inverted bounds rather than producing an empty span, because an
         * inverted bound is a layout bug and an empty span is a legitimate state;
         * collapsing the two would hide the bug.
         */
        fun ofBounds(start: Int, endExclusive: Int): RowSpan {
            require(endExclusive >= start) {
                "row span bounds inverted: start=$start endExclusive=$endExclusive"
            }
            return RowSpan(start, endExclusive - start)
        }

        fun empty(at: Int): RowSpan = RowSpan(at, 0)
    }
}
