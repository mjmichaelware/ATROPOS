/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.territory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerritoryMonitorCostTest {

    /** Hierarchical: each node is checked against its own grants only. */
    private fun hierarchical(nodes: Int): TerritoryCostSnapshot {
        val cost = TerritoryMonitorCost()
        repeat(nodes) { cost.recordCheck(nodes) }
        return cost.snapshot()
    }

    /** Flat: every node compared against every other, the shape being avoided. */
    private fun flat(nodes: Int): TerritoryCostSnapshot {
        val cost = TerritoryMonitorCost()
        repeat(nodes) { repeat(nodes) { cost.recordCheck(nodes) } }
        return cost.snapshot()
    }

    @Test
    fun `a fresh counter records nothing`() {
        val snapshot = TerritoryMonitorCost().snapshot()
        assertEquals(0, snapshot.checks)
        assertEquals(0.0, snapshot.checksPerNode)
        assertTrue(snapshot.isLinearShaped(), "no observations cannot fail the claim")
    }

    @Test
    fun `hierarchical cost stays flat per node as the swarm grows`() {
        val small = hierarchical(4).checksPerNode
        val large = hierarchical(64).checksPerNode

        assertEquals(1.0, small)
        assertEquals(1.0, large, "per-node cost must not grow with node count")
    }

    @Test
    fun `hierarchical checking satisfies the linear claim at scale`() {
        assertTrue(hierarchical(256).isLinearShaped())
    }

    @Test
    fun `the guard actually detects the quadratic shape it exists to catch`() {
        val quadratic = flat(64)

        assertEquals(64.0, quadratic.checksPerNode)
        assertFalse(
            quadratic.isLinearShaped(),
            "a guard that passes a flat bag-of-agents is not guarding anything"
        )
    }

    @Test
    fun `the bound is generous enough not to fire on small constant factors`() {
        val cost = TerritoryMonitorCost()
        // Four checks per node — more than one grant each, still linear.
        repeat(10) { repeat(4) { cost.recordCheck(10) } }

        assertTrue(cost.snapshot().isLinearShaped())
    }

    @Test
    fun `evidence renders both the counts and the verdict`() {
        val rendered = hierarchical(8).render()

        assertTrue(rendered.contains("checks=8"))
        assertTrue(rendered.contains("nodes=8"))
        assertTrue(rendered.contains("linear=true"))
    }

    @Test
    fun `reset clears the counters`() {
        val cost = TerritoryMonitorCost()
        cost.recordCheck(4)
        cost.reset()

        assertEquals(0, cost.snapshot().checks)
    }
}
