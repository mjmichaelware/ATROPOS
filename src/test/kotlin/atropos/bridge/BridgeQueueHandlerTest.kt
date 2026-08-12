/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.queue.ConversationWorkRunner
import atropos.bridge.queue.QueueEntryView
import atropos.bridge.queue.QueueRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeQueueHandlerTest {

    private fun entry(id: String, state: String = "QUEUED") = QueueEntryView(
        id = id,
        task = "build a tracker",
        state = state,
        checkpoint = "QUEUED",
        attempts = 0,
        maxAttempts = 2,
        terminal = state in setOf("COMPLETED", "FAILED", "CANCELLED"),
        failureReason = null,
        evidence = null,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    private class FakeRunner(
        val entries: MutableList<QueueEntryView> = mutableListOf(),
        var outcome: QueueRunOutcome = QueueRunOutcome.NothingToRun("queue is empty"),
        var isThrottled: Boolean = false
    ) : ConversationWorkRunner {
        var ranId: String? = null
        var runCalled = false
        var cancelled: Pair<String, String>? = null
        override fun list(limit: Int) = entries.take(limit)
        override fun find(id: String) = entries.firstOrNull { it.id == id }
        override fun run(id: String?): QueueRunOutcome {
            runCalled = true; ranId = id; return outcome
        }
        override fun cancel(id: String, reason: String): QueueEntryView? {
            cancelled = id to reason
            return entries.firstOrNull { it.id == id }
        }
        override fun throttled() = isThrottled
    }

    private fun request(query: Map<String, String> = emptyMap()) =
        HttpRequest("GET", "/v1/queue", query, emptyMap(), "")

    @Test
    fun listing_returns_entries_with_state_and_attempts() {
        val runner = FakeRunner(mutableListOf(entry("q-1"), entry("q-2")))
        val response = BridgeQueueHandler(runner).list(request())

        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"count\":2"))
        assertTrue(response.body.contains("q-1"))
        assertTrue(response.body.contains("\"state\":\"QUEUED\""))
        assertTrue(response.body.contains("\"maxAttempts\":2"))
    }

    @Test
    fun listing_one_entry_by_id_returns_just_that_entry() {
        val runner = FakeRunner(mutableListOf(entry("q-1"), entry("q-2")))
        val response = BridgeQueueHandler(runner).list(request(mapOf("id" to "q-2")))

        assertEquals(200, response.status)
        assertTrue(response.body.contains("q-2"))
        assertTrue(!response.body.contains("\"count\""), "a single entry is not a list")
    }

    @Test
    fun an_unknown_id_is_a_404_rather_than_an_empty_success() {
        val response = BridgeQueueHandler(FakeRunner()).list(request(mapOf("id" to "nope")))
        assertEquals(404, response.status)
    }

    /**
     * A client-supplied limit is never trusted to bound the response. Any
     * parseable value is clamped into [1, 100]; only an unparseable one falls
     * back to the default.
     */
    @Test
    fun the_limit_is_clamped_rather_than_trusted() {
        val runner = FakeRunner((1..60).map { entry("q-$it") }.toMutableList())
        val handler = BridgeQueueHandler(runner)

        assertTrue(
            handler.list(request(mapOf("limit" to "1000"))).body.contains("\"count\":60"),
            "an oversized limit is capped at 100, so all 60 available entries are returned"
        )
        assertTrue(
            handler.list(request(mapOf("limit" to "-5"))).body.contains("\"count\":1"),
            "a negative limit clamps to the minimum of 1, never to unbounded"
        )
        assertTrue(
            handler.list(request(mapOf("limit" to "abc"))).body.contains("\"count\":20"),
            "an unparseable limit falls back to the default"
        )
    }

    @Test
    fun running_without_an_id_advances_the_next_entry() {
        val runner = FakeRunner(outcome = QueueRunOutcome.Ran(entry("q-1", "COMPLETED"), "ran q-1"))
        val response = BridgeQueueHandler(runner).run(request())

        assertEquals(200, response.status)
        assertTrue(runner.runCalled)
        assertEquals(null, runner.ranId, "no id means next eligible")
        assertTrue(response.body.contains("\"ran\":true"))
        assertTrue(response.body.contains("ran q-1"))
    }

    @Test
    fun running_a_named_entry_passes_the_id_through() {
        val runner = FakeRunner(outcome = QueueRunOutcome.Ran(entry("q-7"), "ran q-7"))
        BridgeQueueHandler(runner).run(request(mapOf("id" to "q-7")))

        assertEquals("q-7", runner.ranId)
    }

    /**
     * A client polling an idle queue must not see an error: nothing eligible is
     * a normal state, not a failure.
     */
    @Test
    fun an_empty_queue_is_a_success_reporting_that_nothing_ran() {
        val runner = FakeRunner(outcome = QueueRunOutcome.NothingToRun("queue is empty"))
        val response = BridgeQueueHandler(runner).run(request())

        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"ran\":false"))
    }

    @Test
    fun a_refused_run_is_a_conflict_and_a_missing_entry_is_a_404() {
        assertEquals(
            409,
            BridgeQueueHandler(FakeRunner(outcome = QueueRunOutcome.Refused("paid locked"))).run(request()).status
        )
        assertEquals(
            404,
            BridgeQueueHandler(FakeRunner(outcome = QueueRunOutcome.Unknown("no such entry"))).run(request()).status
        )
    }

    @Test
    fun cancelling_requires_an_id_and_forwards_the_reason() {
        val runner = FakeRunner(mutableListOf(entry("q-1")))
        val handler = BridgeQueueHandler(runner)

        assertEquals(400, handler.cancel(request()).status)

        val response = handler.cancel(request(mapOf("id" to "q-1", "reason" to "changed my mind")))
        assertEquals(200, response.status)
        assertEquals("q-1" to "changed my mind", runner.cancelled)
    }

    @Test
    fun throttling_is_reported_so_a_client_can_explain_a_stalled_queue() {
        val runner = FakeRunner(mutableListOf(entry("q-1")), isThrottled = true)
        assertTrue(BridgeQueueHandler(runner).list(request()).body.contains("\"throttled\":true"))
    }
}
