/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.core.monitor.ActivityEvent
import atropos.core.monitor.ActivityStage
import atropos.core.monitor.ActivityStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class ActivityProjectionTest {

    private val base: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun event(id: String, stage: ActivityStage, offset: Long, outcome: String = "verified") =
        ActivityEvent(id, base.plusSeconds(offset), stage, "node-$id", outcome, "detail $id")

    @Test
    fun `an empty stream names every missing stage`() {
        val json = ActivityProjection().render(ActivityStream(emptyList()))

        ActivityStage.entries.forEach { stage ->
            assertTrue(json.contains("\"${stage.canonical}\""), "${stage.canonical} must be named")
        }
        assertTrue(json.contains("\"events\":[]"))
        assertTrue(json.contains("\"everyStageReported\":false"))
    }

    @Test
    fun `events are emitted in timestamp order regardless of collection order`() {
        val stream = ActivityStream(
            listOf(event("c", ActivityStage.TEST, 30), event("a", ActivityStage.PLAN, 10))
        )

        val json = ActivityProjection().render(stream)
        assertTrue(json.indexOf("node-a") < json.indexOf("node-c"))
    }

    @Test
    fun `full stage coverage is reported as coverage, not as success`() {
        // Every stage reported, every one of them failed. Coverage is complete;
        // nothing here may read as a healthy run.
        val stream = ActivityStream(
            ActivityStage.entries.mapIndexed { index, stage ->
                event("e$index", stage, index.toLong(), outcome = "blocked")
            }
        )

        val json = ActivityProjection().render(stream)
        assertTrue(json.contains("\"everyStageReported\":true"))
        assertTrue(json.contains("\"missingStages\":[]"))
        assertTrue(json.contains("\"outcome\":\"blocked\""))
        assertTrue(
            !json.contains("\"healthy\"") && !json.contains("\"ok\":true,\"success\""),
            "the projection must not publish a health verdict it did not compute"
        )
    }
}
