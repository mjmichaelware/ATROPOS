/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeEditorHandlerTest {
    @Test
    fun context_exposes_existing_editor_contract_without_new_state() {
        val table = BridgeRoutes().table()
        val response = table.resolve(request("GET", "/v1/editor/context"))

        assertEquals(200, response.status)
        assertTrue(response.body.contains("local-bridge"))
        assertTrue(response.body.contains("answers"))
        assertTrue(response.body.contains("checkpoint"))
        assertTrue(response.body.contains("/v1/editor/selection"))
    }

    @Test
    fun selection_requires_attribution_and_reuses_conversation_owner() {
        var forwarded: HttpRequest? = null
        val handler = BridgeEditorHandler(
            contextProvider = { "{}" },
            sendMessage = {
                forwarded = it
                HttpResponse.json("{\"ok\":true}")
            }
        )

        val refused = handler.sendSelection(request("POST", "/v1/editor/selection", mapOf("selection" to "x", "path" to "src/Main.kt")))
        assertEquals(403, refused.status)

        val accepted = handler.sendSelection(
            request(
                "POST",
                "/v1/editor/selection",
                mapOf("selection" to "token=secret", "path" to "src/Main.kt", "startLine" to "4", "issuedBy" to "neovim")
            )
        )
        assertEquals(200, accepted.status)
        assertEquals("/v1/message", forwarded?.path)
        assertTrue(forwarded?.query?.get("text")?.contains("[editor selection by neovim src/Main.kt:4]") == true)
        assertTrue(forwarded?.query?.get("text")?.contains("token=secret") == false)
    }

    private fun request(method: String, path: String, query: Map<String, String> = emptyMap()) =
        HttpRequest(method, path, query, emptyMap(), "")
}
