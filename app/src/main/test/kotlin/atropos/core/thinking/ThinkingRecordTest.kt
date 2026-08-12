/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThinkingRecordTest {

    private val record = ThinkingRecord(
        nodeId = "n-1",
        lines = listOf(
            ThinkingLine("a", ThinkingDepth.L1, "Extract the parser"),
            ThinkingLine("b", ThinkingDepth.L2, "The parser is bounded, so a long header refuses"),
            ThinkingLine("c", ThinkingDepth.L2, "Callers already handle a refusal"),
            ThinkingLine("d", ThinkingDepth.L3, "Considered truncation; rejected — silent loss")
        )
    )

    @Test
    fun `L1 shows only the outline`() {
        assertEquals(listOf("a"), record.at(ThinkingDepth.L1).map { it.id })
    }

    @Test
    fun `each level adds to the one before it`() {
        // HOE-A08's rule applied to depth: expanding never removes a line the
        // operator was reading.
        val l1 = record.at(ThinkingDepth.L1).map { it.id }.toSet()
        val l2 = record.at(ThinkingDepth.L2).map { it.id }.toSet()
        val l3 = record.at(ThinkingDepth.L3).map { it.id }.toSet()

        assertTrue(l2.containsAll(l1), "L2 must contain everything L1 showed")
        assertTrue(l3.containsAll(l2), "L3 must contain everything L2 showed")
        assertEquals(4, l3.size)
    }

    @Test
    fun `a level is never rendered as its own branch`() {
        // The break this guards: filtering by equality instead of by "at or
        // below" looks correct at every individual level.
        assertFalse(record.at(ThinkingDepth.L2).none { it.minDepth == ThinkingDepth.L1 })
    }

    @Test
    fun `hasMoreThan is false once everything is shown`() {
        assertTrue(record.hasMoreThan(ThinkingDepth.L1))
        assertTrue(record.hasMoreThan(ThinkingDepth.L2))
        assertFalse(record.hasMoreThan(ThinkingDepth.L3))
    }

    @Test
    fun `a record with only an outline offers no expand`() {
        // A drawer that opens onto nothing teaches the operator the gesture
        // means nothing.
        val shallow = ThinkingRecord("n-2", listOf(ThinkingLine("a", ThinkingDepth.L1, "x")))

        assertFalse(shallow.hasMoreThan(ThinkingDepth.L1))
        assertEquals(ThinkingDepth.L1, shallow.deepestAvailable())
    }

    @Test
    fun `an empty record reports nothing available`() {
        val empty = ThinkingRecord("n-3", emptyList())

        assertTrue(empty.isEmpty())
        assertNull(empty.deepestAvailable())
        assertFalse(empty.hasMoreThan(ThinkingDepth.L1))
        assertTrue(empty.at(ThinkingDepth.L3).isEmpty())
    }

    @Test
    fun `depth parses from a level and refuses an unknown one`() {
        assertEquals(ThinkingDepth.L2, ThinkingDepth.fromLevel(2))
        assertNull(ThinkingDepth.fromLevel(4))
        assertNull(ThinkingDepth.fromLevel(0))
    }

    @Test
    fun `the default is collapsed`() {
        assertEquals(ThinkingDepth.L1, ThinkingDepth.DEFAULT)
    }
}

class ThinkingChannelsTest {

    @Test
    fun `each surface starts collapsed`() {
        val channels = ThinkingChannels()

        assertEquals(ThinkingDepth.L1, channels.depthFor("web"))
        assertEquals(ThinkingDepth.L1, channels.depthFor("cli"))
    }

    @Test
    fun `expanding one surface does not move another`() {
        // HOE-E04: terminal deep, web quiet — or the reverse.
        val channels = ThinkingChannels()
        channels.expand("cli", ThinkingDepth.L3)

        assertEquals(ThinkingDepth.L3, channels.depthFor("cli"))
        assertEquals(ThinkingDepth.L1, channels.depthFor("web"))
    }

    @Test
    fun `collapsing one surface leaves the others where they were`() {
        val channels = ThinkingChannels()
        channels.expand("cli", ThinkingDepth.L3)
        channels.expand("web", ThinkingDepth.L2)

        val untouched = channels.collapse("cli")

        assertEquals(ThinkingDepth.L1, channels.depthFor("cli"))
        assertEquals(ThinkingDepth.L2, channels.depthFor("web"))
        assertTrue(untouched.contains("web"))
    }

    @Test
    fun `the engine stores full depth regardless of what any surface asked for`() {
        // The payload is filtered on read, never requested shallower: asking
        // the provider for less would produce different reasoning per surface
        // rather than a different view of the same reasoning.
        val record = ThinkingRecord(
            "n-1",
            listOf(
                ThinkingLine("a", ThinkingDepth.L1, "outline"),
                ThinkingLine("b", ThinkingDepth.L3, "full trace")
            )
        )
        val channels = ThinkingChannels()
        channels.expand("web", ThinkingDepth.L1)

        assertEquals(2, record.lines.size, "stored depth is unaffected by any channel")
        assertEquals(1, record.at(channels.depthFor("web")).size)
    }
}
