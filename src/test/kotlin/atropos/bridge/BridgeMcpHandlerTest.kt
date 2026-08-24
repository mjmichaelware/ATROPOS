/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.core.integration.McpTerritoryBridge
import atropos.core.integration.McpHostManager
import java.nio.file.Files
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

    @Test
    fun `BridgeMcpHandler exposes the configured host status without secrets`() {
        val root = Files.createTempDirectory("bridge-mcp-status")
        Files.writeString(root.resolve("mcp.json"), """{"servers":[{"name":"local","transport":"stdio","enabled":false,"community":false}]}""")
        val handler = BridgeMcpHandler(host = McpHostManager(root))
        val response = handler.status()
        assertEquals(200, response.status)
        assertTrue(response.body.contains("\"name\":\"local\""))
        assertTrue(response.body.contains("\"health\":\"untested\""))
        assertTrue(!response.body.contains("secret"))
    }

    @Test
    fun `BridgeMcpHandler requires explicit server and tool for a call`() {
        val handler = BridgeMcpHandler(host = McpHostManager(Files.createTempDirectory("bridge-mcp-call")))
        val response = handler.call(HttpRequest("POST", "/v1/mcp/call", emptyMap(), emptyMap(), ""))
        assertEquals(400, response.status)
        assertTrue(response.body.contains("server"))
        assertTrue(response.body.contains("tool"))
    }

    @Test
    fun `BridgeMcpHandler gates a tool call before starting the host`() {
        val root = Files.createTempDirectory("bridge-mcp-gate")
        val marker = root.resolve("started")
        val script = root.resolve("mcp.sh")
        Files.writeString(script, "#!/bin/sh\ntouch '${marker.fileName}'\n")
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"local","transport":"stdio","command":"./mcp.sh","enabled":true,"community":false}]}
        """.trimIndent())
        val handler = BridgeMcpHandler(host = McpHostManager(root))
        val response = handler.call(
            HttpRequest(
                "POST",
                "/v1/mcp/call",
                mapOf(
                    "server" to "local",
                    "tool" to "write",
                    "callerId" to "test-client",
                    "paths" to "src/main"
                ),
                emptyMap(),
                ""
            )
        )
        assertEquals(403, response.status)
        assertTrue(!Files.exists(marker))
    }

    @Test
    fun `BridgeMcpHandler reads bounded call identity from a form body`() {
        val root = Files.createTempDirectory("bridge-mcp-body")
        val handler = BridgeMcpHandler(host = McpHostManager(root))
        val response = handler.call(
            HttpRequest(
                "POST",
                "/v1/mcp/call",
                emptyMap(),
                emptyMap(),
                "server=missing&tool=inspect&callerId=body-client&paths=src/main"
            )
        )
        assertEquals(403, response.status)
        assertTrue(!response.body.contains("needs 'server' and 'tool'"))
    }
}
