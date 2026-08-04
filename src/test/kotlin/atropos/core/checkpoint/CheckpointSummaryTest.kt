/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.checkpoint

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckpointSummaryTest {

    private val recorded = Instant.parse("2026-08-04T00:00:00Z")
    private val now = recorded.plusSeconds(600)

    private fun summary(resumable: Boolean = true) = CheckpointSummary(
        goalId = "shg-1",
        nodeId = "node-7",
        phase = "11",
        recordedAt = recorded,
        resumable = resumable,
        evidenceCount = 3,
        nextAction = "advance node-7"
    )

    @Test
    fun `resume is the primary action when the checkpoint is resumable`() {
        assertEquals(CheckpointAction.RESUME, summary().primaryAction)
        assertEquals("Resume", CheckpointAction.RESUME.label)
    }

    @Test
    fun `an unresumable checkpoint offers inspection, never a fresh start`() {
        // Starting over would discard the state without showing why it could
        // not continue.
        val action = summary(resumable = false).primaryAction
        assertEquals(CheckpointAction.INSPECT, action)
        assertTrue(action.label.contains("cannot resume"))
    }

    @Test
    fun `there is no new-run primary action at all`() {
        assertEquals(2, CheckpointAction.entries.size)
        assertTrue(CheckpointAction.entries.none { it.canonical.contains("new") })
    }

    @Test
    fun `age is computed from the recorded time`() {
        assertEquals(10, summary().ageAt(now).toMinutes())
    }

    @Test
    fun `render carries goal, node, age and the primary action`() {
        val rendered = summary().render(now)
        assertTrue(rendered.contains("goal=shg-1"))
        assertTrue(rendered.contains("node=node-7"))
        assertTrue(rendered.contains("age=10m"))
        assertTrue(rendered.contains("primary=resume"))
    }

    @Test
    fun `a checkpoint with no node still renders honestly`() {
        val rendered = summary().copy(nodeId = null, phase = null).render(now)
        assertTrue(rendered.contains("node=none"))
        assertTrue(rendered.contains("phase=none"))
    }
}
