/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.projection.StatusProjection
import atropos.bridge.projection.CheckpointProjection
import atropos.bridge.projection.SixAnswersProjection
import atropos.bridge.queue.ConversationWorkRunner
import atropos.bridge.queue.QueueEntryView
import atropos.bridge.queue.QueueRunOutcome
import atropos.cli.ui.HomeStateProvider
import atropos.core.checkpoint.CheckpointSummary
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeStatusHandlerTest {

    private class FakeWorkRunner : ConversationWorkRunner {
        override fun list(limit: Int): List<QueueEntryView> = listOf(
            QueueEntryView("q-1", "task-1", "PENDING", "COMPILATION", 0, 3, false, null, null, "now", "now")
        )
        override fun find(id: String): QueueEntryView? = null
        override fun run(id: String?): QueueRunOutcome = QueueRunOutcome.NothingToRun("")
        override fun cancel(id: String, reason: String): QueueEntryView? = null
        override fun throttled(): Boolean = false
    }

    @Test
    fun test_status_compilation() {
        val homeState = HomeStateProvider()
        val work = FakeWorkRunner()
        val handler = BridgeStatusHandler(
            homeState = homeState,
            activeProvider = { "fake-provider" },
            sixAnswers = SixAnswersProjection(),
            checkpoint = { CheckpointSummary("P11", true, "build-success", Instant.now(), "node-1") },
            checkpointView = CheckpointProjection(),
            work = work,
            statusView = StatusProjection(),
            clock = { Instant.parse("2026-01-01T00:00:00Z") }
        )

        val response = handler.getStatus()
        assertEquals(200, response.status)

        val body = response.body
        assertTrue(body.contains("\"ok\":true"))
        assertTrue(body.contains("\"activeProvider\":\"fake-provider\""))
        assertTrue(body.contains("\"queueDepth\":1"))
        assertTrue(body.contains("\"engine\":\"atropos\""))
        assertTrue(body.contains("\"answers\""))
        assertTrue(body.contains("\"checkpoint\""))
    }
}
