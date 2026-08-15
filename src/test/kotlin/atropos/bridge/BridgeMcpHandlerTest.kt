/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.core.integration.McpTerritoryBridge
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.PolicyDecisionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeMcpHandlerTest {

    @Test
    fun `BridgeMcpHandler returns 400 when query parameters are missing`() {
        val handler = BridgeMcpHandler()
        val response = handler.judge(HttpRequest("POST", "/v1/mcp/judge", emptyMap(), emptyMap(), ""))
        assertEquals(400, response.status)
        assertTrue(response.body.contains("needs 'callerId', 'operation', and a comma-separated 'paths' list"))
    }

    @Test
    fun `BridgeMcpHandler returns Judged decision when parameters are present`() {
        // Mock McpTerritoryBridge to evaluate to ALLOWED
        val mockBridge = McpTerritoryBridge(setOf("inspect")) { proposal ->
            AgencyDecision(
                proposal = proposal,
                policyDecision = ExecutionPolicyDecision("mock", PolicyDecisionType.ALLOW, PolicyActionClass.SHELL, false, "allowed"),
                disposition = AgencyDisposition.ALLOWED,
                reason = "allowed mock action"
            )
        }
        val handler = BridgeMcpHandler(mockBridge)
        val response = handler.judge(
            HttpRequest(
                method = "POST",
                path = "/v1/mcp/judge",
                query = mapOf(
                    "callerId" to "client-test",
                    "operation" to "inspect",
                    "paths" to "src/main"
                ),
                headers = emptyMap(),
                body = ""
            )
        )
        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"disposition\":\"ALLOWED\""))
        assertTrue(response.body.contains("\"allowed\":true"))
    }
}
