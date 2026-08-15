/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.integration.InboundGateResult
import atropos.core.integration.InboundSource
import atropos.core.integration.InboundToolRequest
import atropos.core.integration.McpTerritoryBridge

internal class BridgeMcpHandler(
    private val mcpBridge: McpTerritoryBridge = McpTerritoryBridge(setOf("inspect", "verify"))
) {
    fun judge(request: HttpRequest): HttpResponse {
        val callerId = request.query["callerId"].orEmpty().ifBlank { field(request.body, "callerId") }
        val operation = request.query["operation"].orEmpty().ifBlank { field(request.body, "operation") }
        val pathsRaw = request.query["paths"].orEmpty().ifBlank { field(request.body, "paths") }
        val paths = pathsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val targetSurface = request.query["targetSurface"].orEmpty().ifBlank { field(request.body, "targetSurface") }.takeIf { it.isNotBlank() }
        val territoryGrantId = request.query["territoryGrantId"].orEmpty().ifBlank { field(request.body, "territoryGrantId") }.takeIf { it.isNotBlank() }

        if (callerId.isBlank() || operation.isBlank() || paths.isEmpty()) {
            return HttpResponse.badRequest(
                "An MCP judge request needs 'callerId', 'operation', and a comma-separated 'paths' list.",
                "POST /v1/mcp/judge?callerId=<id>&operation=<op>&paths=<p1,p2>"
            )
        }

        val inboundRequest = InboundToolRequest(
            source = InboundSource.MCP,
            callerId = callerId,
            operation = operation,
            paths = paths,
            requiresNetwork = false,
            targetSurface = targetSurface,
            territoryGrantId = territoryGrantId
        )

        return when (val result = mcpBridge.judge(inboundRequest)) {
            is InboundGateResult.Judged -> HttpResponse.json(
                JsonWriter.obj(
                    "ok" to JsonWriter.bool(true),
                    "disposition" to JsonWriter.str(result.decision.disposition.name),
                    "allowed" to JsonWriter.bool(result.decision.disposition.name == "ALLOWED"),
                    "reason" to JsonWriter.str(result.decision.reason)
                )
            )
            is InboundGateResult.Refused -> HttpResponse.refusal(
                400,
                "mcp-refused",
                result.reason,
                "Expose the operation or correct the caller type."
            )
        }
    }

    private fun field(body: String, key: String): String =
        body.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            .orEmpty()
}
