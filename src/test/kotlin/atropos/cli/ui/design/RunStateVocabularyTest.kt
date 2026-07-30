/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two states added for Source Doc 4 — [RunState.PLANNING] and
 * [RunState.REVIEW_REQUIRED] — exist to stop "thinking" reading as "queued" and
 * "your turn" reading as "the machine is busy". These tests pin the properties
 * that distinction rests on, and the redundancy rule the whole enum is built to
 * satisfy.
 */
class RunStateVocabularyTest {

    @Test
    fun planning_is_its_own_state_and_does_not_borrow_running_s_motion_or_accent() {
        assertEquals("planning", RunState.PLANNING.label)
        assertFalse(RunState.PLANNING.animated)
        assertEquals(Role.STATUS_IDLE, RunState.PLANNING.role)
        assertTrue(RunState.PLANNING != RunState.QUEUED)
    }

    @Test
    fun review_required_asks_for_a_decision_rather_than_showing_activity() {
        assertEquals("review required", RunState.REVIEW_REQUIRED.label)
        assertFalse(
            RunState.REVIEW_REQUIRED.animated,
            "motion reads as progress, but nothing moves until the operator acts"
        )
        assertEquals(Role.STATUS_WAITING, RunState.REVIEW_REQUIRED.role)
        assertTrue(RunState.REVIEW_REQUIRED != RunState.WAITING)
    }

    @Test
    fun no_two_states_share_a_glyph_in_either_unicode_or_ascii() {
        assertEquals(
            RunState.entries.size,
            RunState.entries.map { it.glyph }.toSet().size,
            "duplicate unicode glyphs: ${RunState.entries.map { it.glyph }}"
        )
        assertEquals(
            RunState.entries.size,
            RunState.entries.map { it.asciiGlyph }.toSet().size,
            "duplicate ascii glyphs: ${RunState.entries.map { it.asciiGlyph }}"
        )
        assertEquals(
            RunState.entries.size,
            RunState.entries.map { it.label }.toSet().size
        )
    }

    @Test
    fun the_ascii_glyphs_stay_single_cell_and_printable() {
        RunState.entries.forEach { state ->
            assertEquals(1, state.asciiGlyph.length, "${state.name} ascii glyph is not one char")
            val code = state.asciiGlyph.single().code
            assertTrue(code in 33..126, "${state.name} ascii glyph is not printable ascii")
        }
    }

    @Test
    fun only_cancelled_renders_struck_through() {
        assertEquals(
            listOf(RunState.CANCELLED),
            RunState.entries.filter { it.struckThrough }
        )
    }

    @Test
    fun an_unreadable_boolean_becomes_unknown_and_never_a_failure() {
        assertEquals(RunState.UNKNOWN, RunState.ofNullable(null))
        assertEquals(RunState.COMPLETE, RunState.ofNullable(true))
        assertEquals(RunState.FAILED, RunState.ofNullable(false))
    }
}
