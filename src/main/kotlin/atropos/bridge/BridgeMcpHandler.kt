/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.core.integration.InboundSource
import atropos.core.integration.McpTerritoryBridge

internal class BridgeMcpHandler(
    private val mcpBridge: McpTerritoryBridge = McpTerritoryBridge(setOf("inspect", "verify"))
) {
    private val delegate = BridgeInboundToolHandler(InboundSource.MCP, mcpBridge::judge, "MCP")

    fun judge(request: atropos.bridge.http.HttpRequest): atropos.bridge.http.HttpResponse = delegate.judge(request)
}
