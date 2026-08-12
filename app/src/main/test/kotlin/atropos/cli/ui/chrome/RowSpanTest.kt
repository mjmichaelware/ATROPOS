/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A negative row height used to be reachable through bare `Int` subtraction and
 * then clamped by whichever `coerceAtLeast` was nearest, which is how a redraw
 * ends up one row off the previous frame. These tests hold the guard that stops
 * such a span existing at all.
 */
class RowSpanTest {

    @Test
    fun a_negative_height_cannot_be_constructed() {
        val failure = runCatching { RowSpan(start = 4, rows = -1) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "got $failure")
        assertTrue(failure.message.orEmpty().contains("height must be non-negative"), failure?.message.orEmpty())
    }

    @Test
    fun a_negative_start_cannot_be_constructed() {
        val failure = runCatching { RowSpan(start = -1, rows = 3) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "got $failure")
        assertTrue(failure.message.orEmpty().contains("start must be non-negative"), failure?.message.orEmpty())
    }

    @Test
    fun inverted_bounds_are_refused_rather_than_collapsed_into_an_empty_span() {
        val failure = runCatching { RowSpan.ofBounds(start = 9, endExclusive = 4) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "got $failure")
        assertTrue(failure.message.orEmpty().contains("inverted"), failure?.message.orEmpty())
    }

    @Test
    fun equal_bounds_are_a_legitimate_empty_span() {
        val span = RowSpan.ofBounds(start = 7, endExclusive = 7)

        assertEquals(RowSpan(7, 0), span)
        assertTrue(span.isEmpty)
        assertNull(span.lastRow)
        assertEquals(7, span.endExclusive)
    }

    @Test
    fun bounds_are_half_open_so_the_last_row_is_one_before_the_end() {
        val span = RowSpan.ofBounds(start = 3, endExclusive = 8)

        assertEquals(RowSpan(3, 5), span)
        assertEquals(8, span.endExclusive)
        assertEquals(7, span.lastRow)
        assertFalse(span.isEmpty)
    }

    @Test
    fun a_span_contains_its_first_row_but_not_its_end_row() {
        val span = RowSpan(start = 3, rows = 2)

        assertTrue(span.contains(3))
        assertTrue(span.contains(4))
        assertFalse(span.contains(5))
        assertFalse(span.contains(2))
        assertFalse(RowSpan.empty(3).contains(3))
    }

    @Test
    fun adjacent_spans_abut_without_overlapping() {
        val header = RowSpan(0, 2)
        val transcript = RowSpan(2, 6)

        assertTrue(header.abuts(transcript))
        assertFalse(header.overlaps(transcript))
        assertFalse(transcript.abuts(header))
    }

    @Test
    fun spans_sharing_a_row_are_reported_as_overlapping() {
        assertTrue(RowSpan(0, 3).overlaps(RowSpan(2, 4)))
        assertTrue(RowSpan(2, 4).overlaps(RowSpan(0, 3)))
        assertFalse(RowSpan(0, 3).overlaps(RowSpan(3, 4)))
    }

    @Test
    fun an_empty_span_never_overlaps_anything() {
        assertFalse(RowSpan.empty(2).overlaps(RowSpan(0, 10)))
        assertFalse(RowSpan(0, 10).overlaps(RowSpan.empty(2)))
        assertFalse(RowSpan.empty(2).overlaps(RowSpan.empty(2)))
    }
}
