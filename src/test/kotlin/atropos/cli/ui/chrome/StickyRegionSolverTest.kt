/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.chrome

import atropos.cli.ui.design.Breakpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HOE-B01's invariant is "no bounce on redraw": at an unchanged size the header
 * and input must land on exactly the same rows every frame, and a resize delta
 * must be absorbed entirely by the transcript. A terminal too short to hold the
 * chrome must be refused rather than clamped into overlapping regions.
 */
class StickyRegionSolverTest {

    private fun resolved(
        totalRows: Int,
        columns: Int = 80,
        headerRows: Int = 3,
        inputRows: Int = 2
    ): StickyRegions {
        val plan = StickyRegionSolver.solve(totalRows, columns, headerRows, inputRows)
        assertTrue(plan is StickyRegionPlan.Resolved, plan.describe())
        return plan.regions
    }

    @Test
    fun the_transcript_takes_every_row_the_chrome_does_not_claim() {
        val regions = resolved(totalRows = 24)

        assertEquals(RowSpan(0, 3), regions.header)
        assertEquals(RowSpan(3, 19), regions.transcript)
        assertEquals(RowSpan(22, 2), regions.input)
        assertEquals(emptyList(), regions.violations())
        assertTrue(regions.isSound)
        assertEquals(Breakpoint.MEDIUM, regions.breakpoint)
    }

    @Test
    fun a_terminal_too_short_for_header_input_and_one_transcript_row_is_refused() {
        val plan = StickyRegionSolver.solve(totalRows = 4, columns = 80, headerRows = 3, inputRows = 2)

        assertTrue(plan is StickyRegionPlan.Refused, plan.describe())
        assertEquals(StickyRegionPlan.Reason.TOO_SHORT_FOR_CHROME, plan.reason)
        assertNull(plan.regionsOrNull())
        assertTrue(plan.detail.contains("needs 6 rows"), plan.detail)
        assertTrue(plan.detail.contains("has 4"), plan.detail)
        assertTrue(plan.describe().contains("too short for chrome"), plan.describe())
    }

    @Test
    fun the_exact_minimum_height_resolves_with_a_single_transcript_row() {
        val minimum = StickyRegionSolver.minimumRows(headerRows = 3, inputRows = 2)
        assertEquals(6, minimum)

        val regions = resolved(totalRows = minimum)

        assertEquals(StickyRegionSolver.MINIMUM_TRANSCRIPT_ROWS, regions.transcript.rows)
        assertEquals(emptyList(), regions.violations())
    }

    @Test
    fun an_empty_viewport_is_refused_before_any_arithmetic_happens() {
        listOf(
            StickyRegionSolver.solve(totalRows = 0, columns = 80, headerRows = 1, inputRows = 1),
            StickyRegionSolver.solve(totalRows = -5, columns = 80, headerRows = 1, inputRows = 1),
            StickyRegionSolver.solve(totalRows = 24, columns = 0, headerRows = 1, inputRows = 1)
        ).forEach { plan ->
            assertTrue(plan is StickyRegionPlan.Refused, plan.describe())
            assertEquals(StickyRegionPlan.Reason.EMPTY_VIEWPORT, plan.reason)
            assertNull(plan.regionsOrNull())
        }
    }

    @Test
    fun a_negative_region_request_is_named_as_such_rather_than_silently_clamped() {
        val plan = StickyRegionSolver.solve(totalRows = 24, columns = 80, headerRows = -1, inputRows = 2)

        assertTrue(plan is StickyRegionPlan.Refused, plan.describe())
        assertEquals(StickyRegionPlan.Reason.NEGATIVE_REGION_REQUEST, plan.reason)
        assertTrue(plan.detail.contains("header=-1"), plan.detail)

        val negativeInput =
            StickyRegionSolver.solve(totalRows = 24, columns = 80, headerRows = 3, inputRows = -2)
        assertTrue(negativeInput is StickyRegionPlan.Refused, negativeInput.describe())
        assertEquals(StickyRegionPlan.Reason.NEGATIVE_REGION_REQUEST, negativeInput.reason)
    }

    @Test
    fun repeated_solves_at_the_same_size_produce_identical_geometry() {
        val first = resolved(totalRows = 30, columns = 100)
        val second = resolved(totalRows = 30, columns = 100)
        val third = resolved(totalRows = 30, columns = 100)

        assertEquals(first, second)
        assertEquals(second, third)
        assertTrue(first.chromeMatches(second))
        assertTrue(second.chromeMatches(third))
        assertEquals(first.transcript, third.transcript)
    }

    @Test
    fun growing_the_terminal_moves_no_chrome_and_gives_every_new_row_to_the_transcript() {
        val before = resolved(totalRows = 24)
        val after = resolved(totalRows = 30)

        assertTrue(after.absorbsResizeFrom(before))
        assertEquals(before.header, after.header)
        assertEquals(before.input.rows, after.input.rows)
        assertEquals(before.transcript.rows + 6, after.transcript.rows)
        assertEquals(before.transcript.start, after.transcript.start)
    }

    @Test
    fun shrinking_the_terminal_also_takes_the_delta_out_of_the_transcript_only() {
        val before = resolved(totalRows = 40)
        val after = resolved(totalRows = 25)

        assertTrue(after.absorbsResizeFrom(before))
        assertEquals(before.transcript.rows - 15, after.transcript.rows)
        assertEquals(RowSpan(0, 3), after.header)
        assertEquals(after.totalRows, after.input.endExclusive)
    }

    @Test
    fun a_chrome_height_change_is_reported_as_a_bounce_rather_than_a_resize() {
        val before = resolved(totalRows = 24, headerRows = 3)
        val taller = resolved(totalRows = 24, headerRows = 5)

        assertFalse(taller.absorbsResizeFrom(before))
        assertFalse(taller.chromeMatches(before))
    }

    @Test
    fun chrome_with_no_header_or_input_still_partitions_soundly() {
        val regions = resolved(totalRows = 10, headerRows = 0, inputRows = 0)

        assertEquals(RowSpan(0, 0), regions.header)
        assertEquals(RowSpan(0, 10), regions.transcript)
        assertEquals(RowSpan(10, 0), regions.input)
        assertEquals(emptyList(), regions.violations())
    }

    @Test
    fun a_caller_supplied_transcript_floor_raises_the_height_the_solver_demands() {
        val plan = StickyRegionSolver.solve(
            totalRows = 10,
            columns = 80,
            headerRows = 3,
            inputRows = 2,
            minimumTranscriptRows = 8
        )

        assertTrue(plan is StickyRegionPlan.Refused, plan.describe())
        assertEquals(StickyRegionPlan.Reason.TOO_SHORT_FOR_CHROME, plan.reason)
        assertTrue(plan.detail.contains("needs 13 rows"), plan.detail)
        assertEquals(13, StickyRegionSolver.minimumRows(3, 2, minimumTranscriptRows = 8))
    }

    @Test
    fun a_zero_transcript_floor_is_still_treated_as_one_row() {
        assertEquals(1, StickyRegionSolver.minimumRows(0, 0, minimumTranscriptRows = 0))

        val plan = StickyRegionSolver.solve(
            totalRows = 5,
            columns = 80,
            headerRows = 3,
            inputRows = 2,
            minimumTranscriptRows = 0
        )
        assertTrue(plan is StickyRegionPlan.Refused, plan.describe())
        assertEquals(StickyRegionPlan.Reason.TOO_SHORT_FOR_CHROME, plan.reason)
    }

    @Test
    fun a_resolved_plan_describes_the_partition_it_produced() {
        val plan = StickyRegionSolver.solve(totalRows = 24, columns = 80, headerRows = 3, inputRows = 2)

        assertEquals("header=3 transcript=19 input=2 of 24 rows", plan.describe())
    }

    @Test
    fun violations_name_the_rule_a_hand_built_partition_breaks() {
        val unanchored = StickyRegions(
            totalRows = 10,
            columns = 80,
            header = RowSpan(0, 2),
            transcript = RowSpan(2, 3),
            input = RowSpan(5, 2)
        )

        val violations = unanchored.violations()
        assertFalse(unanchored.isSound)
        assertTrue(violations.any { it.contains("anchored to the bottom row") }, "$violations")
        assertTrue(violations.any { it.contains("cover 7 rows of 10") }, "$violations")
    }

    @Test
    fun violations_report_an_overlap_and_an_empty_transcript_by_name() {
        val overlapping = StickyRegions(
            totalRows = 4,
            columns = 80,
            header = RowSpan(0, 3),
            transcript = RowSpan(3, 0),
            input = RowSpan(2, 2)
        )

        val violations = overlapping.violations()
        assertTrue(violations.any { it.contains("transcript has no rows") }, "$violations")
        assertTrue(violations.any { it.contains("regions overlap") }, "$violations")
    }

    @Test
    fun the_chrome_reads_its_width_class_from_the_shared_breakpoint_vocabulary() {
        assertEquals(Breakpoint.COMPACT, resolved(totalRows = 24, columns = 40).breakpoint)
        assertEquals(Breakpoint.MEDIUM, resolved(totalRows = 24, columns = 80).breakpoint)
        assertEquals(Breakpoint.WIDE, resolved(totalRows = 24, columns = 120).breakpoint)
        assertEquals(Breakpoint.ULTRA, resolved(totalRows = 24, columns = 200).breakpoint)
    }
}
