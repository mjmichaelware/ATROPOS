/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.interrupt

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterruptControllerTest {

    private fun controller(): InterruptController {
        var tick = 0L
        val base = Instant.parse("2026-08-04T00:00:00Z")
        return InterruptController(clock = { base.plusSeconds(tick++) })
    }

    @Test
    fun `all three levels exist with distinct resumability`() {
        assertTrue(InterruptLevel.SOFT.resumable)
        assertTrue(InterruptLevel.FREEZE.resumable)
        assertFalse(InterruptLevel.HARD.resumable, "a hard stop abandons work in flight")
        assertEquals(3, InterruptLevel.entries.size)
    }

    @Test
    fun `a request is pending until the loop takes it`() {
        val c = controller()
        c.request(InterruptLevel.FREEZE, "operator")

        assertTrue(c.state().isPending)
        assertFalse(c.state().isStopped, "asking is not stopping")
        assertTrue(c.shouldStop())
        assertTrue(c.state().render().contains("has not stopped yet"))
    }

    @Test
    fun `freeze records the exact resume point`() {
        val c = controller()
        c.request(InterruptLevel.FREEZE, "operator")

        val outcome = c.take("dag-node-7")

        assertTrue(outcome is InterruptOutcome.Taken)
        assertEquals("dag-node-7", (outcome as InterruptOutcome.Taken).taken.resumePoint)
        assertTrue(c.state().isStopped)
        assertFalse(c.shouldStop(), "a taken interrupt must not keep stopping the loop")
    }

    @Test
    fun `a resumable interrupt without a resume point is refused`() {
        val c = controller()
        c.request(InterruptLevel.FREEZE, "operator")

        val outcome = c.take(null)

        assertTrue(outcome is InterruptOutcome.Refused)
        assertFalse(c.state().isStopped, "a refused take must leave the run running")
    }

    @Test
    fun `a hard stop records no resume point rather than a fabricated one`() {
        val c = controller()
        c.request(InterruptLevel.HARD, "operator")

        val outcome = c.take("dag-node-7")

        assertTrue(outcome is InterruptOutcome.Taken)
        assertNull(
            (outcome as InterruptOutcome.Taken).taken.resumePoint,
            "a hard stop has no consistent position to resume from"
        )
        assertTrue(c.state().render().contains("not resumable"))
    }

    @Test
    fun `a stronger interrupt supersedes a weaker pending one`() {
        val c = controller()
        c.request(InterruptLevel.SOFT, "operator")
        c.request(InterruptLevel.HARD, "operator")

        assertEquals(InterruptLevel.HARD, c.state().requested?.level)
    }

    @Test
    fun `a weaker interrupt does not downgrade a stronger pending one`() {
        val c = controller()
        c.request(InterruptLevel.HARD, "operator")
        c.request(InterruptLevel.SOFT, "operator")

        assertEquals(InterruptLevel.HARD, c.state().requested?.level)
    }

    @Test
    fun `taking twice is refused`() {
        val c = controller()
        c.request(InterruptLevel.SOFT, "operator")
        c.take("node-1")

        assertTrue(c.take("node-2") is InterruptOutcome.Refused)
    }

    @Test
    fun `taking without a request is refused`() {
        assertTrue(controller().take("node-1") is InterruptOutcome.Refused)
    }

    @Test
    fun `clearing lets a resumed run proceed`() {
        val c = controller()
        c.request(InterruptLevel.FREEZE, "operator")
        c.take("node-1")

        c.clear()

        assertFalse(c.shouldStop())
        assertFalse(c.state().isStopped)
        assertEquals("no interrupt", c.state().render())
    }

    @Test
    fun `a fresh controller never stops a run`() {
        assertFalse(controller().shouldStop())
    }
}
