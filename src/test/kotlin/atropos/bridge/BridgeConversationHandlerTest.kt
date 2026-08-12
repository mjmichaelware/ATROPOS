/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationResponder
import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.conversation.TurnAuthor
import atropos.bridge.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeConversationHandlerTest {

    private fun request(
        method: String = "POST",
        path: String = "/v1/message",
        query: Map<String, String> = emptyMap(),
        body: String = ""
    ) = HttpRequest(method, path, query, emptyMap(), body)

    private fun handler(
        store: BridgeConversationStore = BridgeConversationStore(),
        responder: BridgeConversationResponder = BridgeConversationResponder { "reply to: $it" },
        sessions: BridgeSessionStore? = null
    ) = BridgeConversationHandler(store, responder, sessions = sessions)

    @Test
    fun posting_a_message_records_the_operator_turn_and_the_reply() {
        val store = BridgeConversationStore()
        val response = handler(store).postMessage(request(body = """{"text":"hello engine"}"""))

        assertEquals(200, response.status)
        val turns = store.transcript()
        assertEquals(2, turns.size)
        assertEquals(TurnAuthor.OPERATOR, turns[0].author)
        assertEquals("hello engine", turns[0].text)
        assertEquals(TurnAuthor.ENGINE, turns[1].author)
        assertTrue(turns[1].text.contains("reply to: hello engine"))
    }

    @Test
    fun the_text_may_also_arrive_as_a_query_parameter() {
        val store = BridgeConversationStore()
        val response = handler(store).postMessage(request(query = mapOf("text" to "from query")))

        assertEquals(200, response.status)
        assertEquals("from query", store.transcript().first().text)
    }

    @Test
    fun an_empty_message_is_refused_and_records_nothing() {
        val store = BridgeConversationStore()
        val response = handler(store).postMessage(request(body = """{"text":"   "}"""))

        assertEquals(400, response.status)
        assertTrue(store.transcript().isEmpty(), "a refused message must not enter the transcript")
    }

    @Test
    fun an_oversized_message_is_refused() {
        val store = BridgeConversationStore()
        val huge = "x".repeat(9_000)
        val response = handler(store).postMessage(request(body = """{"text":"$huge"}"""))

        assertEquals(400, response.status)
        assertTrue(store.transcript().isEmpty())
    }

    /**
     * A responder that throws must not lose the operator's turn or leave the
     * client with no reply at all.
     */
    @Test
    fun a_failing_responder_still_produces_an_engine_turn() {
        val store = BridgeConversationStore()
        val exploding = BridgeConversationResponder { error("provider exploded") }
        val response = handler(store, exploding).postMessage(request(body = """{"text":"hi"}"""))

        assertEquals(200, response.status)
        val turns = store.transcript()
        assertEquals(2, turns.size)
        assertEquals(TurnAuthor.ENGINE, turns[1].author)
        assertTrue(turns[1].text.contains("could not answer"), "failure is reported as a turn")
    }

    @Test
    fun transcript_is_returned_and_honours_the_after_cursor() {
        val store = BridgeConversationStore()
        val h = handler(store)
        h.postMessage(request(body = """{"text":"one"}"""))

        val all = h.getMessages(request(method = "GET", path = "/v1/messages"))
        assertEquals(200, all.status)
        assertTrue(all.body.contains("\"count\":2"))

        val firstId = store.transcript().first().id
        val delta = h.getMessages(
            request(method = "GET", path = "/v1/messages", query = mapOf("after" to firstId))
        )
        assertTrue(delta.body.contains("\"count\":1"), "cursor returns only later turns")
    }

    @Test
    fun turns_are_rendered_with_id_author_text_and_timestamp() {
        val h = handler()
        h.postMessage(request(body = """{"text":"shape check"}"""))
        val body = h.getMessages(request(method = "GET", path = "/v1/messages")).body

        assertTrue(body.contains("\"id\""))
        assertTrue(body.contains("\"author\":\"operator\""))
        assertTrue(body.contains("\"author\":\"engine\""))
        assertTrue(body.contains("\"text\""))
        assertTrue(body.contains("\"at\""))
    }

    /**
     * The transcript is a rendered surface, so the no-raw-secret rule applies
     * to it exactly as it does to the terminal.
     */
    @Test
    fun a_message_that_looks_like_a_credential_is_not_stored_raw() {
        val store = BridgeConversationStore()
        val secret = "sk-live-ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        handler(store).postMessage(request(body = """{"text":"my key is $secret"}"""))

        val recorded = store.transcript().first().text
        assertFalse(recorded.contains(secret), "a credential must not survive into the transcript verbatim")
    }

    @Test
    fun session_query_isolates_transcripts() {
        val store = BridgeConversationStore()
        val sessions = BridgeSessionStore()
        val s1 = sessions.create("Session 1")
        val s2 = sessions.create("Session 2")

        val h = handler(store, sessions = sessions)

        // Post to s1
        val r1 = h.postMessage(request(query = mapOf("session" to s1.id), body = """{"text":"message for s1"}"""))
        assertEquals(200, r1.status)

        // Post to s2
        val r2 = h.postMessage(request(query = mapOf("session" to s2.id), body = """{"text":"message for s2"}"""))
        assertEquals(200, r2.status)

        // Retrieve messages from s1
        val m1 = h.getMessages(request(method = "GET", path = "/v1/messages", query = mapOf("session" to s1.id)))
        assertTrue(m1.body.contains("message for s1"))
        assertFalse(m1.body.contains("message for s2"))

        // Retrieve messages from s2
        val m2 = h.getMessages(request(method = "GET", path = "/v1/messages", query = mapOf("session" to s2.id)))
        assertTrue(m2.body.contains("message for s2"))
        assertFalse(m2.body.contains("message for s1"))
    }

    @Test
    fun unknown_session_returns_404() {
        val store = BridgeConversationStore()
        val sessions = BridgeSessionStore()
        val h = handler(store, sessions = sessions)

        val r = h.postMessage(request(query = mapOf("session" to "unknown-session"), body = """{"text":"hello"}"""))
        assertEquals(404, r.status)

        val rGet = h.getMessages(request(method = "GET", path = "/v1/messages", query = mapOf("session" to "unknown-session")))
        assertEquals(404, rGet.status)
    }

    @Test
    fun omitting_session_uses_default_store() {
        val store = BridgeConversationStore()
        val sessions = BridgeSessionStore()
        val h = handler(store, sessions = sessions)

        val r = h.postMessage(request(body = """{"text":"hello default"}"""))
        assertEquals(200, r.status)

        val m = h.getMessages(request(method = "GET", path = "/v1/messages"))
        assertTrue(m.body.contains("hello default"))
        assertEquals(2, store.transcript().size)
    }
}
