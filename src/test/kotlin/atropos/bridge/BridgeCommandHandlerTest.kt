/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeCommandHandlerTest {
    @Test
    fun command_success_payload_redacts_renderer_output() {
        val handler = BridgeCommandHandler {
            BridgeCommandOutput("api_key=sk-live-success-secret-123456789", exited = true)
        }
        val response = handler.execute(
            HttpRequest(
                "POST",
                "/v1/cli",
                emptyMap(),
                emptyMap(),
                "{\"command\":\"/status\",\"issuedBy\":\"operator\"}"
            )
        )

        assertTrue(response.body.contains("\"ok\":true"))
        assertFalse(response.body.contains("sk-live-success-secret-123456789"))
        assertTrue(response.body.contains("<redacted:api_key>"))
    }

    @Test
    fun command_failure_payload_redacts_provider_secret() {
        val handler = BridgeCommandHandler {
            error("provider rejected api_key=sk-live-secret-123456789")
        }
        val response = handler.execute(
            HttpRequest(
                "POST",
                "/v1/cli",
                emptyMap(),
                emptyMap(),
                "{\"command\":\"/status\",\"issuedBy\":\"operator\"}"
            )
        )

        assertTrue(response.body.contains("\"ok\":false"))
        assertFalse(response.body.contains("sk-live-secret-123456789"))
        assertTrue(response.body.contains("<redacted:api_key>"))
    }
}
