/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.supremacy.AirGapViolationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpGovernanceTest {

    @Test
    fun `transport interceptor identifies stdio vs http`() {
        val interceptor = McpTransportSecurityInterceptor()
        assertEquals(TransportType.LOCAL_STDIO, interceptor.resolveTransportType("stdio"))
        assertEquals(TransportType.REMOTE_HTTP, interceptor.resolveTransportType("http"))
        assertEquals(TransportType.REMOTE_SSE, interceptor.resolveTransportType("sse"))
        assertEquals(TransportType.REMOTE_WEBSOCKET, interceptor.resolveTransportType("ws"))
    }

    @Test
    fun `health probe marks disabled server unhealthy`() {
        val hostManager = GenericMcpHostManager(java.nio.file.Paths.get("."))
        val supervisor = McpHealthProbeSupervisor(hostManager)
        val config = McpServerConfig(
            name = "test-server",
            transport = "stdio",
            command = "test",
            args = emptyList(),
            enabled = false,
            community = true
        )
        val status = supervisor.probeServerHealth(config)
        assertEquals(McpServerHealth.UNHEALTHY, status.health)
        assertTrue(status.errorDetail?.contains("disabled") == true)
    }

    @Test
    fun `registry discovery formats json snippet with disabled by default`() {
        val discovery = McpRegistryDiscoveryService()
        val item = RegistrySearchResult(
            serverName = "mock-tool",
            description = "Mock description",
            vendor = "Community",
            suggestedCommand = "mock-bin",
            suggestedArgs = listOf("--run"),
            verifiedSecurity = false
        )
        val snippet = discovery.formatMcpJsonSnippet(item)
        assertTrue(snippet.contains("\"enabled\": false"))
        assertTrue(snippet.contains("\"community\": true"))
    }
}
