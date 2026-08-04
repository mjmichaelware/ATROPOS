/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.core.thinking.ThinkingDepth
import atropos.core.thinking.ThinkingLine
import atropos.core.thinking.ThinkingRecord
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThinkingProjectionTest {

    private val record = ThinkingRecord(
        "n-1",
        listOf(
            ThinkingLine("a", ThinkingDepth.L1, "outline line"),
            ThinkingLine("b", ThinkingDepth.L2, "reasoning line"),
            ThinkingLine("c", ThinkingDepth.L3, "trace line")
        )
    )

    @Test
    fun `an absent record is reported as absent`() {
        val json = ThinkingProjection().render(null, ThinkingDepth.L1)

        assertTrue(json.contains("\"present\":false"))
        assertTrue(json.contains("No reasoning was recorded"))
    }

    @Test
    fun `an empty record is absence, not a node that thought about nothing`() {
        val json = ThinkingProjection().render(ThinkingRecord("n-2", emptyList()), ThinkingDepth.L1)

        assertTrue(json.contains("\"present\":false"))
    }

    @Test
    fun `L1 emits only the outline and says more exists`() {
        val json = ThinkingProjection().render(record, ThinkingDepth.L1)

        assertTrue(json.contains("outline line"))
        assertFalse(json.contains("reasoning line"))
        assertTrue(json.contains("\"hasMore\":true"))
    }

    @Test
    fun `each depth adds rather than replaces`() {
        val l2 = ThinkingProjection().render(record, ThinkingDepth.L2)

        assertTrue(l2.contains("outline line"), "L2 must still carry what L1 showed")
        assertTrue(l2.contains("reasoning line"))
        assertFalse(l2.contains("trace line"))
    }

    @Test
    fun `the deepest level reports nothing further`() {
        val l3 = ThinkingProjection().render(record, ThinkingDepth.L3)

        assertTrue(l3.contains("trace line"))
        assertTrue(l3.contains("\"hasMore\":false"))
    }

    @Test
    fun `a record with only an outline never advertises an expand`() {
        // A drawer that opens onto nothing teaches the gesture means nothing.
        val shallow = ThinkingRecord("n-3", listOf(ThinkingLine("a", ThinkingDepth.L1, "only")))
        val json = ThinkingProjection().render(shallow, ThinkingDepth.L1)

        assertTrue(json.contains("\"hasMore\":false"))
    }

    @Test
    fun `every level is advertised so the surface need not hard-code them`() {
        val json = ThinkingProjection().render(record, ThinkingDepth.L1)

        ThinkingDepth.entries.forEach { assertTrue(json.contains(it.label)) }
    }
}
