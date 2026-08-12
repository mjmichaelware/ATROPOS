/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.conversation.TurnAuthor
import atropos.bridge.http.HttpRequest
import atropos.core.approval.PendingApprovalStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeEventsHandlerTest {

    @Test
    fun test_event_hub_notifications_and_cursor_scoping() {
        val tempDir = Files.createTempDirectory("events-test-")
        try {
            val approvals = PendingApprovalStore(tempDir)
            val sessions = BridgeSessionStore()
            val defaultStore = BridgeConversationStore()

            // Initialize event hub
            BridgeEventHub.clear()

            val handler = BridgeEventsHandler(
                work = null,
                approvals = approvals,
                sessions = sessions,
                defaultStore = defaultStore
            )

            // Initial poll: no events
            val response1 = handler.getEvents(HttpRequest("GET", "/v1/events", mapOf("after" to "0"), emptyMap(), ""))
            assertEquals(200, response1.status)
            assertTrue(response1.body.contains("\"count\":0"))

            // 1. Trigger conversation turn event in sessions
            val s1 = sessions.create()
            sessions.append(s1.id, TurnAuthor.OPERATOR, "First message")

            val response2 = handler.getEvents(HttpRequest("GET", "/v1/events", mapOf("after" to "0"), emptyMap(), ""))
            assertEquals(200, response2.status)
            assertTrue(response2.body.contains("\"count\":1"))
            assertTrue(response2.body.contains("turn_appended"))
            assertTrue(response2.body.contains(s1.id))

            // 2. Poll with cursor after first event: count should be 0
            val response3 = handler.getEvents(HttpRequest("GET", "/v1/events", mapOf("after" to "1"), emptyMap(), ""))
            assertTrue(response3.body.contains("\"count\":0"))

            // 3. Trigger approval event
            approvals.record("prop-1", "policy-actor", "rebuild", emptyList(), "requires audit")
            
            val response4 = handler.getEvents(HttpRequest("GET", "/v1/events", mapOf("after" to "1"), emptyMap(), ""))
            assertTrue(response4.body.contains("\"count\":1"))
            assertTrue(response4.body.contains("approval_raised"))
            assertTrue(response4.body.contains("prop-1"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
