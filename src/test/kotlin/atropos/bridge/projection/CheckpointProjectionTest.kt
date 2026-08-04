/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.core.checkpoint.CheckpointSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckpointProjectionTest {

    private val now: Instant = Instant.parse("2026-01-01T12:00:00Z")

    private fun summary(resumable: Boolean) = CheckpointSummary(
        goalId = "g-1",
        nodeId = "n-4",
        phase = "implement",
        recordedAt = now.minusSeconds(600),
        resumable = resumable,
        evidenceCount = 3,
        nextAction = "run the verifier"
    )

    @Test
    fun `an absent checkpoint is reported as absent, not as an empty one`() {
        val json = CheckpointProjection().render(null, now)

        assertTrue(json.contains("\"present\":false"))
        assertTrue(json.contains("No checkpoint has been recorded"))
        assertFalse(json.contains("\"goalId\""), "absence must not carry a goal")
    }

    @Test
    fun `a resumable checkpoint makes Resume the primary action`() {
        val json = CheckpointProjection().render(summary(resumable = true), now)

        assertTrue(json.contains("\"primaryAction\":{\"id\":\"resume\""))
        assertTrue(json.contains("\"ageMinutes\":10"))
    }

    @Test
    fun `an unresumable checkpoint offers inspection rather than starting over`() {
        val json = CheckpointProjection().render(summary(resumable = false), now)

        assertTrue(json.contains("\"primaryAction\":{\"id\":\"inspect\""))
        assertFalse(json.contains("\"resumable\":true"))
    }

    @Test
    fun `no action offered anywhere starts a new run`() {
        // HOE-B04: the checkpoint's primary is Resume, explicitly not "new
        // chat". If the engine never emits such an action there is nothing for
        // a surface to bind that control to.
        listOf(true, false).forEach { resumable ->
            val json = CheckpointProjection().render(summary(resumable), now)
            assertFalse(json.contains("new", ignoreCase = true) && json.contains("\"id\":\"new"))
            assertFalse(json.contains("\"id\":\"restart\""))
        }
    }

    @Test
    fun `exactly one action is marked primary`() {
        val json = CheckpointProjection().render(summary(resumable = true), now)

        val primaries = Regex("\"primary\":true").findAll(json).count()
        kotlin.test.assertEquals(1, primaries)
    }
}
