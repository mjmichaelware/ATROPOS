/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HOE-B02: rows start collapsed and a first expand reveals L1 *only*. The
 * regression these tests exist against is the single "verbose" keypress that
 * dumps L1 to L4 at once, which is indistinguishable from having no disclosure.
 */
class DisclosureExpansionTest {

    private fun content(): DisclosureContent = DisclosureContent.of(
        summary = "thought for 2s",
        l1 = listOf("considered two options"),
        l2 = listOf("option a: patch", "option b: revert"),
        l3 = listOf("weighed blast radius")
    )

    @Test
    fun a_row_starts_collapsed_without_the_caller_saying_so() {
        val row = DisclosureRow(DisclosureRowKind.THINKING, content())

        assertEquals(DisclosureState.Collapsed, row.state)
        assertEquals(DisclosureState.Collapsed, DisclosureState.DEFAULT)
        assertFalse(row.state.isOpen)
        assertNull(row.state.revealed)
        assertEquals(emptyList(), row.visibleLines())
    }

    @Test
    fun the_first_expand_reveals_l1_only_and_not_the_deeper_levels() {
        val expansion = DisclosureRow.collapsed(DisclosureRowKind.THINKING, content()).expand()

        assertNotNull(expansion)
        assertEquals(DisclosureState.Expanded(DisclosureLevel.L1), expansion.row.state)
        assertEquals(listOf("considered two options"), expansion.reveal.added)
        assertEquals(emptyList(), expansion.reveal.retained)
        assertEquals(listOf("considered two options"), expansion.row.visibleLines())
    }

    @Test
    fun each_later_expand_advances_exactly_one_level_and_adds_only_that_level() {
        var row = DisclosureRow.collapsed(DisclosureRowKind.PLAN, content())
        val steps = mutableListOf<List<String>>()
        val reached = mutableListOf<DisclosureLevel>()

        while (true) {
            val expansion = row.expand() ?: break
            steps += expansion.reveal.added
            reached += expansion.row.state.revealed!!
            row = expansion.row
        }

        assertEquals(listOf(DisclosureLevel.L1, DisclosureLevel.L2, DisclosureLevel.L3), reached)
        assertEquals(
            listOf(
                listOf("considered two options"),
                listOf("option a: patch", "option b: revert"),
                listOf("weighed blast radius")
            ),
            steps
        )
        assertEquals(content().visibleAt(DisclosureLevel.L3), row.visibleLines())
    }

    @Test
    fun an_expand_at_the_deepest_populated_level_returns_null_instead_of_repainting() {
        val deep = DisclosureState.Expanded(DisclosureLevel.L3)

        assertNull(DisclosureExpansion.nextLevel(deep, content()))
        assertFalse(DisclosureExpansion.canExpand(deep, content()))
        assertNull(DisclosureExpansion.expand(deep, content()))
        assertNull(DisclosureRow(DisclosureRowKind.PLAN, content(), deep).expand())
    }

    @Test
    fun a_leaf_row_never_offers_an_expand() {
        val leaf = DisclosureRow.collapsed(
            DisclosureRowKind.EVIDENCE,
            DisclosureContent.leaf("nothing cited")
        )

        assertFalse(leaf.canExpand)
        assertNull(leaf.expand())
        assertNull(DisclosureExpansion.nextLevel(DisclosureState.Collapsed, leaf.content))
    }

    @Test
    fun collapse_closes_the_row_completely_and_re_expanding_starts_again_at_l1() {
        var row = DisclosureRow.collapsed(DisclosureRowKind.ENGINE, content())
        row = row.expand()!!.row
        row = row.expand()!!.row
        assertEquals(DisclosureState.Expanded(DisclosureLevel.L2), row.state)

        val closed = row.collapse()
        assertEquals(DisclosureState.Collapsed, closed.state)
        assertEquals(emptyList(), closed.visibleLines())

        val reopened = closed.expand()!!
        assertEquals(DisclosureState.Expanded(DisclosureLevel.L1), reopened.row.state)
        assertEquals(listOf("considered two options"), reopened.row.visibleLines())
    }

    @Test
    fun a_reveal_reports_what_stayed_on_screen_separately_from_what_it_added() {
        val reveal = DisclosureExpansion.expand(
            DisclosureState.Expanded(DisclosureLevel.L1),
            content()
        )

        assertNotNull(reveal)
        assertEquals(listOf("considered two options"), reveal.retained)
        assertEquals(listOf("option a: patch", "option b: revert"), reveal.added)
        assertEquals(content().visibleAt(DisclosureLevel.L2), reveal.visible())
    }

    @Test
    fun a_shrinking_expand_cannot_be_constructed() {
        val failure = assertFailsWith<IllegalArgumentException> {
            DisclosureReveal(
                from = DisclosureState.Expanded(DisclosureLevel.L3),
                to = DisclosureState.Expanded(DisclosureLevel.L2),
                content = content()
            )
        }

        assertTrue(
            failure.message.orEmpty().contains("may not go shallower"),
            failure.message.orEmpty()
        )
    }

    @Test
    fun a_reveal_pointing_at_a_level_with_no_content_cannot_be_constructed() {
        val failure = assertFailsWith<IllegalArgumentException> {
            DisclosureReveal(
                from = DisclosureState.Expanded(DisclosureLevel.L3),
                to = DisclosureState.Expanded(DisclosureLevel.L4),
                content = content()
            )
        }

        assertTrue(
            failure.message.orEmpty().contains("no content at L4"),
            failure.message.orEmpty()
        )
    }

    @Test
    fun expanded_state_means_everything_through_that_level_not_only_that_level() {
        val state: DisclosureState = DisclosureState.Expanded(DisclosureLevel.L3)

        assertTrue(state.isOpen)
        assertEquals(DisclosureLevel.L3, state.revealed)
        assertEquals("Expanded(L3)", state.toString())
        assertEquals(
            listOf(
                "considered two options",
                "option a: patch",
                "option b: revert",
                "weighed blast radius"
            ),
            DisclosureRow(DisclosureRowKind.PLAN, content(), state).visibleLines()
        )
    }
}
