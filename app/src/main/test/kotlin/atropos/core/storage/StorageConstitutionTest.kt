/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageConstitutionTest {

    private fun constitution(used: Long, ceiling: Long = 1_000) = StorageConstitution(
        ceilingBytes = ceiling,
        classes = listOf(
            StorageClass("active-run", RetentionTier.HOT, used / 2),
            StorageClass("recent", RetentionTier.WARM, used / 4),
            StorageClass("archive", RetentionTier.COLD, used / 4)
        )
    )

    private val gate = FreeSpaceGate()

    @Test
    fun `the active run is never reclaimable`() {
        assertFalse(RetentionTier.HOT.reclaimable)
        assertFalse(RetentionTier.RECLAIM_ORDER.contains(RetentionTier.HOT))
        assertTrue(constitution(400).reclaimable().none { it.tier == RetentionTier.HOT })
    }

    @Test
    fun `reclaim order takes the cheapest loss first`() {
        assertEquals(
            listOf(RetentionTier.DELETE, RetentionTier.COLD, RetentionTier.WARM),
            RetentionTier.RECLAIM_ORDER
        )
        val order = constitution(400).reclaimable().map { it.tier }
        assertEquals(listOf(RetentionTier.COLD, RetentionTier.WARM), order)
    }

    @Test
    fun `an ordinary allocation well under the ceiling is allowed silently`() {
        assertEquals(FreeSpaceDecision.Allowed, gate.evaluate(constitution(100), 100))
    }

    @Test
    fun `approaching the ceiling warns but does not refuse`() {
        val decision = gate.evaluate(constitution(800), 60)
        assertTrue(decision is FreeSpaceDecision.AllowedWithWarning)
        assertTrue(decision.permitted)
    }

    @Test
    fun `an allocation that would cross the refuse band is refused`() {
        val decision = gate.evaluate(constitution(900), 40)
        assertTrue(decision is FreeSpaceDecision.Refused)
        assertFalse(decision.permitted)
    }

    @Test
    fun `every refusal names what could be reclaimed instead of just saying no`() {
        val decision = gate.evaluate(constitution(900), 80) as FreeSpaceDecision.Refused
        assertTrue(decision.reclaimableBytes > 0, "a refusal must give the operator a next action")
        assertTrue(decision.reason.isNotBlank())
    }

    @Test
    fun `the emergency band is distinguishable from an ordinary refusal`() {
        val emergency = gate.evaluate(constitution(950), 40) as FreeSpaceDecision.Refused
        assertTrue(emergency.emergency)
        val ordinary = gate.evaluate(constitution(900), 30) as FreeSpaceDecision.Refused
        assertFalse(ordinary.emergency)
    }

    @Test
    fun `a zero ceiling refuses everything rather than dividing by zero`() {
        val decision = gate.evaluate(StorageConstitution(ceilingBytes = 0), 1)
        assertTrue(decision is FreeSpaceDecision.Refused)
    }

    @Test
    fun `a negative request is refused rather than treated as freeing space`() {
        assertTrue(gate.evaluate(constitution(100), -500) is FreeSpaceDecision.Refused)
    }

    @Test
    fun `remaining bytes never go negative`() {
        assertEquals(0, constitution(2_000).remainingBytes)
    }

    @Test
    fun `wouldExceed answers the ceiling question directly`() {
        val c = constitution(900)
        assertFalse(c.wouldExceed(50))
        assertTrue(c.wouldExceed(200))
    }

    @Test
    fun `render carries the numbers an operator needs`() {
        val rendered = constitution(500).render()
        assertTrue(rendered.contains("used=500"))
        assertTrue(rendered.contains("ceiling=1000"))
        assertTrue(rendered.contains("reclaimable="))
    }
}
