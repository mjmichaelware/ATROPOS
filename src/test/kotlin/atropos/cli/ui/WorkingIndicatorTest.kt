/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The indicator has to answer "is this alive?" at a glance.
 *
 * A single braille character rotating at the end of a line was technically an
 * animation and practically invisible on a phone while a run scrolled past.
 */
class WorkingIndicatorTest {

    private val buffer = AnimatedThinkingBuffer()

    @Test
    fun the_pulse_is_wide_enough_to_see() {
        val frame = buffer.frame(0)

        assertTrue(
            frame.length >= 8,
            "the indicator is ${frame.length} cells wide — too small to read peripherally"
        )
    }

    @Test
    fun the_pulse_travels_rather_than_flickering_in_place() {
        val brightest = (0 until 16).map { index -> buffer.frame(index).indexOfFirst { it == '█' } }

        assertTrue(brightest.distinct().size > 3, "the pulse never moved: $brightest")
        assertTrue(brightest.none { it < 0 }, "some frame had no pulse at all: $brightest")
    }

    @Test
    fun it_returns_rather_than_jumping_back_to_the_start() {
        // A pulse that leaps from the right edge to the left reads as a
        // glitch. Out and back reads as travel.
        val positions = (0 until 32).map { index -> buffer.frame(index).indexOfFirst { it == '█' } }
        val jumps = positions.zipWithNext { a, b -> kotlin.math.abs(a - b) }

        assertTrue(jumps.all { it <= 1 }, "the pulse teleported: $positions")
    }

    @Test
    fun the_cycle_repeats_exactly() {
        val period = 16
        assertEquals(buffer.frame(0), buffer.frame(period))
    }

    @Test
    fun the_message_travels_with_the_frame() {
        assertTrue(buffer.render(3, "Thinking").contains("Thinking"))
    }
}
