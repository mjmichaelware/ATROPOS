/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.monitor

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityStreamTest {

    private val base = Instant.parse("2026-08-04T00:00:00Z")

    private fun event(id: String, seconds: Long, stage: ActivityStage, outcome: String = "verified") =
        ActivityEvent(id, base.plusSeconds(seconds), stage, "subject-$id", outcome, "detail")

    @Test
    fun `all eight C3-P19 stages exist`() {
        assertEquals(8, ActivityStage.entries.size)
        listOf("plan", "provider", "tool", "diff", "test", "verifier", "artifact", "deploy")
            .forEach { assertTrue(ActivityStage.fromCanonical(it) != null, "missing stage $it") }
    }

    @Test
    fun `an unknown stage does not resolve`() {
        assertNull(ActivityStage.fromCanonical("vibes"))
    }

    @Test
    fun `ordering is deterministic across identical inputs`() {
        val events = listOf(event("b", 5, ActivityStage.TEST), event("a", 1, ActivityStage.PLAN))
        assertEquals(
            ActivityStream(events).ordered().map { it.id },
            ActivityStream(events.reversed()).ordered().map { it.id }
        )
    }

    @Test
    fun `simultaneous events break ties by id rather than collection order`() {
        val events = listOf(event("z", 1, ActivityStage.PLAN), event("a", 1, ActivityStage.TOOL))
        assertEquals(listOf("a", "z"), ActivityStream(events).ordered().map { it.id })
    }

    @Test
    fun `a gap is reported as a gap`() {
        val stream = ActivityStream(listOf(event("a", 1, ActivityStage.PLAN)))
        assertTrue(stream.missingStages().contains(ActivityStage.DEPLOY))
        assertFalse(stream.isComplete())
    }

    @Test
    fun `completeness means the pipeline ran, not that it succeeded`() {
        val allStages = ActivityStage.entries.mapIndexed { index, stage ->
            event("e$index", index.toLong(), stage, outcome = "blocked")
        }
        val stream = ActivityStream(allStages)
        assertTrue(stream.isComplete(), "every stage reported")
        // Conflating the two would let a fully-failed run look complete.
        assertTrue(stream.ordered().all { it.outcome == "blocked" })
    }

    @Test
    fun `filtering by stage returns only that stage`() {
        val stream = ActivityStream(
            listOf(event("a", 1, ActivityStage.PLAN), event("b", 2, ActivityStage.TEST))
        )
        assertEquals(listOf("b"), stream.stage(ActivityStage.TEST).map { it.id })
    }

    @Test
    fun `an empty stream is missing everything rather than complete`() {
        val stream = ActivityStream(emptyList())
        assertEquals(8, stream.missingStages().size)
        assertFalse(stream.isComplete())
    }

    @Test
    fun `render carries counts and the completeness verdict`() {
        val rendered = ActivityStream(listOf(event("a", 1, ActivityStage.PLAN))).render()
        assertTrue(rendered.contains("events=1"))
        assertTrue(rendered.contains("1/8"))
        assertTrue(rendered.contains("complete=false"))
    }
}
