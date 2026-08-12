/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeSession
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BridgeSessionHandlerTest {

    private fun request(
        method: String = "GET",
        path: String = "/v1/sessions",
        query: Map<String, String> = emptyMap(),
        body: String = ""
    ) = HttpRequest(method, path, query, emptyMap(), body)

    @Test
    fun test_list_all_sessions() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)
        val s1 = store.create("First")
        val s2 = store.create("Second")

        val response = handler.list(request())
        assertEquals(200, response.status)
        assertTrue(response.body.contains("First"))
        assertTrue(response.body.contains("Second"))
        assertTrue(response.body.contains("\"count\":2"))
    }

    @Test
    fun test_list_by_id() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)
        val s = store.create("Unique Session")

        val response = handler.list(request(query = mapOf("id" to s.id)))
        assertEquals(200, response.status)
        assertTrue(response.body.contains(s.id))
        assertTrue(response.body.contains("Unique Session"))
    }

    @Test
    fun test_list_by_unknown_id_returns_404() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)

        val response = handler.list(request(query = mapOf("id" to "session-unknown-123")))
        assertEquals(404, response.status)
        assertTrue(response.body.contains("session-unknown"))
    }

    @Test
    fun test_create_session() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)

        val response = handler.create(request(method = "POST", query = mapOf("title" to "My New Session")))
        assertEquals(200, response.status)
        assertTrue(response.body.contains("My New Session"))
        assertTrue(response.body.contains("\"ok\":true"))
        assertEquals(1, store.list().size)
    }

    @Test
    fun test_recent_returns_resumable_false_on_fresh_store() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)

        val response = handler.recent()
        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"resumable\":false"))
        assertTrue(response.body.contains("\"session\":null") || response.body.contains("\"session\":\"null\""))
    }

    @Test
    fun test_recent_returns_resumable_true_when_sessions_exist() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)
        store.create("Recent Title")

        val response = handler.recent()
        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"resumable\":true"))
        assertTrue(response.body.contains("Recent Title"))
    }

    @Test
    fun test_delete_validation_and_success() {
        val store = BridgeSessionStore()
        val handler = BridgeSessionHandler(store)
        
        // No ID query param
        val badResponse = handler.delete(request(method = "POST"))
        assertEquals(400, responseStatus = badResponse.status)
        assertTrue(badResponse.body.contains("Deleting a conversation needs an 'id'"))

        // ID that does not exist
        val notFoundResponse = handler.delete(request(method = "POST", query = mapOf("id" to "non-existent")))
        assertEquals(404, notFoundResponse.status)

        // Successful delete
        val s = store.create()
        val okResponse = handler.delete(request(method = "POST", query = mapOf("id" to s.id)))
        assertEquals(200, okResponse.status)
        assertTrue(okResponse.body.contains("\"ok\":true"))
        assertFalse(store.exists(s.id))
    }

    // Helper to bypass name collision
    private fun assertEquals(expected: Int, responseStatus: Int) {
        kotlin.test.assertEquals(expected, responseStatus)
    }
}
