/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HOE-A08 says information is never removed by an expand. [DisclosureContent]
 * makes that a property of the data shape, so these tests state the property
 * itself — every deeper view begins with the shallower one verbatim — and the two
 * malformed shapes that would produce an expand revealing nothing.
 */
class DisclosureContentTest {

    private fun threeLevels(): DisclosureContent = DisclosureContent.of(
        summary = "ran the compile gate",
        l1 = listOf("gate: compile", "verdict: pass"),
        l2 = listOf("module: cli", "module: core"),
        l3 = listOf("javac -Xlint:all", "exit 0")
    )

    @Test
    fun a_leaf_row_has_nothing_to_expand_into() {
        val content = DisclosureContent.leaf("no detail captured")

        assertTrue(content.isLeaf)
        assertNull(content.deepest)
        assertEquals(emptyList(), content.visibleAt(DisclosureLevel.L1))
        assertFalse(content.hasDeeperThan(DisclosureLevel.L1))
        assertEquals("no detail captured", content.summary)
    }

    @Test
    fun the_deepest_populated_level_is_the_floor_a_row_can_reach() {
        assertEquals(DisclosureLevel.L3, threeLevels().deepest)
        assertFalse(threeLevels().isLeaf)
        assertTrue(threeLevels().hasDeeperThan(DisclosureLevel.L2))
        assertFalse(threeLevels().hasDeeperThan(DisclosureLevel.L3))
        assertFalse(threeLevels().hasDeeperThan(DisclosureLevel.L4))
    }

    @Test
    fun a_deeper_view_appends_to_the_shallower_one_instead_of_replacing_it() {
        val content = threeLevels()

        assertEquals(listOf("gate: compile", "verdict: pass"), content.visibleAt(DisclosureLevel.L1))
        assertEquals(
            listOf("gate: compile", "verdict: pass", "module: cli", "module: core"),
            content.visibleAt(DisclosureLevel.L2)
        )
        assertEquals(
            listOf(
                "gate: compile",
                "verdict: pass",
                "module: cli",
                "module: core",
                "javac -Xlint:all",
                "exit 0"
            ),
            content.visibleAt(DisclosureLevel.L3)
        )
    }

    @Test
    fun every_deeper_level_pair_keeps_the_shallower_view_as_a_verbatim_prefix() {
        val content = DisclosureContent.of(
            summary = "four deep",
            l1 = listOf("a1"),
            l2 = listOf("b1", "b2"),
            l3 = listOf("c1"),
            l4 = listOf("d1", "d2", "d3")
        )
        val levels = DisclosureLevel.ordered()

        levels.forEach { shallower ->
            levels.filter { it.depth > shallower.depth }.forEach { deeper ->
                val shallow = content.visibleAt(shallower)
                val deep = content.visibleAt(deeper)
                assertEquals(
                    shallow,
                    deep.take(shallow.size),
                    "${deeper.label} does not start with ${shallower.label}"
                )
                assertTrue(
                    deep.size >= shallow.size,
                    "${deeper.label} shows fewer lines than ${shallower.label}"
                )
            }
        }
    }

    @Test
    fun the_summary_is_never_repeated_as_a_revealed_line() {
        val content = threeLevels()

        assertFalse(content.visibleAt(DisclosureLevel.L4).contains(content.summary))
        assertEquals("ran the compile gate", content.summary)
    }

    @Test
    fun a_level_gap_is_rejected_where_the_row_is_authored() {
        val failure = assertFailsWith<IllegalArgumentException> {
            DisclosureContent.of(
                summary = "gapped",
                additions = mapOf(
                    DisclosureLevel.L1 to listOf("headline"),
                    DisclosureLevel.L3 to listOf("diagnostics")
                )
            )
        }

        assertTrue(
            failure.message.orEmpty().contains("contiguous"),
            failure.message.orEmpty()
        )
    }

    @Test
    fun a_level_holding_only_blank_lines_is_rejected_as_a_dead_keypress() {
        val failure = assertFailsWith<IllegalArgumentException> {
            DisclosureContent.of(
                summary = "blank body",
                additions = mapOf(DisclosureLevel.L1 to listOf("   ", ""))
            )
        }

        assertTrue(
            failure.message.orEmpty().contains("blank-only"),
            failure.message.orEmpty()
        )
    }

    @Test
    fun blank_lines_beside_real_ones_are_dropped_rather_than_shown_as_gaps() {
        val content = DisclosureContent.of(
            summary = "mixed",
            l1 = listOf("real line", "  ", "another")
        )

        assertEquals(listOf("real line", "another"), content.visibleAt(DisclosureLevel.L1))
    }

    @Test
    fun a_positional_build_that_skips_a_level_is_refused_too() {
        assertFailsWith<IllegalArgumentException> {
            DisclosureContent.of(summary = "skipped l1", l2 = listOf("supporting"))
        }
    }

    @Test
    fun surrounding_whitespace_is_stripped_from_the_summary() {
        assertEquals("tidy", DisclosureContent.leaf("  tidy  ").summary)
        assertEquals("tidy", DisclosureContent.of("  tidy  ", l1 = listOf("x")).summary)
    }

    @Test
    fun levels_are_ordered_shallowest_first_and_stop_at_l4() {
        assertEquals(
            listOf(DisclosureLevel.L1, DisclosureLevel.L2, DisclosureLevel.L3, DisclosureLevel.L4),
            DisclosureLevel.ordered()
        )
        assertEquals(DisclosureLevel.L1, DisclosureLevel.SHALLOWEST)
        assertEquals(DisclosureLevel.L4, DisclosureLevel.DEEPEST)
        assertEquals(DisclosureLevel.L2, DisclosureLevel.L1.deeper())
        assertNull(DisclosureLevel.L4.deeper())
        assertEquals(listOf(1, 2, 3, 4), DisclosureLevel.ordered().map { it.depth })
    }

    @Test
    fun a_level_covers_itself_and_everything_shallower_but_nothing_deeper() {
        assertTrue(DisclosureLevel.L3.covers(DisclosureLevel.L1))
        assertTrue(DisclosureLevel.L3.covers(DisclosureLevel.L3))
        assertFalse(DisclosureLevel.L3.covers(DisclosureLevel.L4))
        assertEquals(
            listOf(DisclosureLevel.L1, DisclosureLevel.L2, DisclosureLevel.L3),
            DisclosureLevel.L3.throughHere()
        )
    }
}
