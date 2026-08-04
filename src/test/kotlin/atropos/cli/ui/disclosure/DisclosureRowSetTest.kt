/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The five HOE-B02 rows always appear in the same order, absent rows are omitted
 * rather than drawn empty, and opening one row must not move the others — an
 * operator reading Evidence must not have Thinking unfold under their cursor.
 */
class DisclosureRowSetTest {

    private fun twoLevel(tag: String): DisclosureContent = DisclosureContent.of(
        summary = "$tag summary",
        l1 = listOf("$tag l1"),
        l2 = listOf("$tag l2")
    )

    private fun set(): DisclosureRowSet = DisclosureRowSet.of(
        mapOf(
            DisclosureRowKind.CHECKPOINT to twoLevel("checkpoint"),
            DisclosureRowKind.THINKING to twoLevel("thinking"),
            DisclosureRowKind.EVIDENCE to twoLevel("evidence")
        )
    )

    @Test
    fun rows_appear_in_doc_order_regardless_of_how_the_caller_built_its_map() {
        assertEquals(
            listOf(
                DisclosureRowKind.THINKING,
                DisclosureRowKind.EVIDENCE,
                DisclosureRowKind.CHECKPOINT
            ),
            set().rows.map { it.kind }
        )
    }

    @Test
    fun the_five_row_labels_are_the_words_hoe_b02_uses() {
        assertEquals(
            listOf("Thinking", "Plan", "Evidence", "Engine", "Checkpoint"),
            DisclosureRowKind.ordered().map { it.label }
        )
    }

    @Test
    fun a_row_with_no_content_is_omitted_rather_than_drawn_as_an_empty_expandable() {
        val rows = set()

        assertNull(rows.row(DisclosureRowKind.PLAN))
        assertNull(rows.row(DisclosureRowKind.ENGINE))
        assertEquals(3, rows.rows.size)
        assertFalse(rows.isEmpty)
        assertTrue(DisclosureRowSet.EMPTY.isEmpty)
        assertEquals(emptyList(), DisclosureRowSet.of(emptyMap()).rows)
    }

    @Test
    fun every_row_in_a_new_set_is_collapsed() {
        assertTrue(set().rows.all { it.state == DisclosureState.Collapsed })
        assertTrue(set().rows.all { it.visibleLines().isEmpty() })
        assertTrue(set().rows.all { it.canExpand })
    }

    @Test
    fun expanding_one_row_leaves_every_other_row_untouched() {
        val change = set().expand(DisclosureRowKind.EVIDENCE)

        assertNotNull(change)
        assertEquals(
            DisclosureState.Expanded(DisclosureLevel.L1),
            change.set.row(DisclosureRowKind.EVIDENCE)!!.state
        )
        assertEquals(listOf("evidence l1"), change.reveal.added)
        assertEquals(
            DisclosureState.Collapsed,
            change.set.row(DisclosureRowKind.THINKING)!!.state
        )
        assertEquals(
            DisclosureState.Collapsed,
            change.set.row(DisclosureRowKind.CHECKPOINT)!!.state
        )
    }

    @Test
    fun each_row_tracks_its_own_depth_so_two_rows_can_be_open_at_different_levels() {
        var rows = set()
        rows = rows.expand(DisclosureRowKind.THINKING)!!.set
        rows = rows.expand(DisclosureRowKind.THINKING)!!.set
        rows = rows.expand(DisclosureRowKind.CHECKPOINT)!!.set

        assertEquals(
            DisclosureState.Expanded(DisclosureLevel.L2),
            rows.row(DisclosureRowKind.THINKING)!!.state
        )
        assertEquals(
            DisclosureState.Expanded(DisclosureLevel.L1),
            rows.row(DisclosureRowKind.CHECKPOINT)!!.state
        )
        assertEquals(
            DisclosureState.Collapsed,
            rows.row(DisclosureRowKind.EVIDENCE)!!.state
        )
        assertEquals(
            listOf("thinking l1", "thinking l2"),
            rows.row(DisclosureRowKind.THINKING)!!.visibleLines()
        )
    }

    @Test
    fun expanding_an_absent_row_returns_null_so_the_screen_is_left_alone() {
        assertNull(set().expand(DisclosureRowKind.PLAN))
        assertNull(set().collapse(DisclosureRowKind.PLAN))
        assertNull(DisclosureRowSet.EMPTY.expand(DisclosureRowKind.THINKING))
    }

    @Test
    fun expanding_a_fully_revealed_row_returns_null_rather_than_an_identical_set() {
        var rows = set()
        rows = rows.expand(DisclosureRowKind.EVIDENCE)!!.set
        rows = rows.expand(DisclosureRowKind.EVIDENCE)!!.set

        assertNull(rows.expand(DisclosureRowKind.EVIDENCE))
        assertFalse(rows.row(DisclosureRowKind.EVIDENCE)!!.canExpand)
    }

    @Test
    fun expanding_returns_a_new_set_and_leaves_the_previous_snapshot_intact() {
        val before = set()
        val after = before.expand(DisclosureRowKind.THINKING)!!.set

        assertEquals(
            DisclosureState.Collapsed,
            before.row(DisclosureRowKind.THINKING)!!.state
        )
        assertEquals(
            DisclosureState.Expanded(DisclosureLevel.L1),
            after.row(DisclosureRowKind.THINKING)!!.state
        )
    }

    @Test
    fun collapse_all_closes_every_open_row_at_once() {
        var rows = set()
        rows = rows.expand(DisclosureRowKind.THINKING)!!.set
        rows = rows.expand(DisclosureRowKind.EVIDENCE)!!.set

        val closed = rows.collapseAll()

        assertTrue(closed.rows.all { it.state == DisclosureState.Collapsed })
        assertEquals(3, closed.rows.size)
    }

    @Test
    fun collapsing_one_row_keeps_the_other_rows_open() {
        var rows = set()
        rows = rows.expand(DisclosureRowKind.THINKING)!!.set
        rows = rows.expand(DisclosureRowKind.EVIDENCE)!!.set

        val closed = rows.collapse(DisclosureRowKind.THINKING)!!

        assertEquals(DisclosureState.Collapsed, closed.row(DisclosureRowKind.THINKING)!!.state)
        assertEquals(
            DisclosureState.Expanded(DisclosureLevel.L1),
            closed.row(DisclosureRowKind.EVIDENCE)!!.state
        )
    }
}
