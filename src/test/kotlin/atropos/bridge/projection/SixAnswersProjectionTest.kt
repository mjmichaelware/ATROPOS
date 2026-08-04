/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.cli.ui.DashboardRenderer
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.RunState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SixAnswersProjectionTest {

    private val projection = SixAnswersProjection()

    private fun state(
        objective: String = "ship the bridge",
        queueReadable: Boolean = true,
        running: List<DashboardRenderer.WorkItem> = emptyList()
    ) = DashboardRenderer.DashboardState(
        answers = DashboardRenderer.SixAnswers(
            objective = DashboardRenderer.Answer(objective, Health.VERIFIED),
            doing = DashboardRenderer.Answer("working", Health.PENDING),
            why = DashboardRenderer.Answer("because", Health.VERIFIED),
            progress = DashboardRenderer.Answer("1/2", Health.PENDING),
            next = DashboardRenderer.Answer("/agent run", Health.PENDING),
            evidence = DashboardRenderer.Answer("none", Health.UNKNOWN)
        ),
        queueReadable = queueReadable,
        runningWork = running,
        provider = "groq"
    )

    @Test
    fun `all six answers cross the wire`() {
        val json = projection.render(state())

        listOf("objective", "doing", "why", "progress", "next", "evidence").forEach {
            assertTrue(json.contains("\"$it\""), "answer '$it' missing from payload")
        }
    }

    @Test
    fun `every answer carries a non-colour signal alongside its health`() {
        val json = projection.render(state())

        assertTrue(json.contains("\"health\""))
        assertTrue(json.contains("\"signal\""))
        // Section E: colour must never be the only channel.
        assertTrue(json.contains("\"signal\":\"verified\""))
        assertTrue(json.contains("\"signal\":\"unknown\""))
    }

    @Test
    fun `an unreadable queue is distinguishable from an empty one`() {
        val unreadable = projection.render(state(queueReadable = false))
        val empty = projection.render(state(queueReadable = true))

        assertTrue(unreadable.contains("\"readable\":false"))
        assertTrue(empty.contains("\"readable\":true"))
    }

    @Test
    fun `secret-bearing answer text is redacted at the render boundary`() {
        val secret = "sk-" + "B".repeat(24)

        val json = projection.render(state(objective = "use key $secret now"))

        assertFalse(json.contains(secret), "HOE-A10: a raw secret must never reach a surface")
    }

    @Test
    fun `secret-bearing work item text is redacted too`() {
        val secret = "sk-" + "C".repeat(24)
        val json = projection.render(
            state(
                running = listOf(
                    DashboardRenderer.WorkItem(
                        id = "job-1",
                        title = "rotate $secret",
                        state = RunState.RUNNING,
                        detail = "in flight"
                    )
                )
            )
        )

        assertFalse(json.contains(secret))
    }

    @Test
    fun `work item state uses the Doc 4 term, not the runtime spelling`() {
        val json = projection.render(
            state(
                running = listOf(
                    DashboardRenderer.WorkItem("j", "t", RunState.RUNNING, "d")
                )
            )
        )

        assertTrue(json.contains("\"state\":\"working\""), "RUNNING must render as Doc 4's 'working'")
    }

    @Test
    fun `emitted json escapes quotes so a crafted answer cannot break the payload`() {
        val json = projection.render(state(objective = """say "hi" \ then"""))

        assertTrue(json.contains("\\\""))
        assertFalse(json.contains("""say "hi""""))
    }
}
