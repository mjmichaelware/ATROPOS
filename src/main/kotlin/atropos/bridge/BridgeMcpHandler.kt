/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.core.integration.InboundSource
import atropos.core.integration.InboundGateResult
import atropos.core.integration.InboundToolRequest
import atropos.core.integration.McpHostManager
import atropos.core.integration.McpTerritoryBridge
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter

internal class BridgeMcpHandler(
    private val mcpBridge: McpTerritoryBridge = McpTerritoryBridge(setOf("inspect", "verify")),
    private val host: McpHostManager? = null
) {
    private val delegate = BridgeInboundToolHandler(InboundSource.MCP, mcpBridge::judge, "MCP")

    fun judge(request: atropos.bridge.http.HttpRequest): atropos.bridge.http.HttpResponse = delegate.judge(request)

    fun call(request: atropos.bridge.http.HttpRequest): HttpResponse {
        val manager = host ?: return HttpResponse.refusal(
            501,
            "mcp-host-not-wired",
            "This bridge build has no MCP host bound.",
            "Start the engine normally so the local mcp.json host is attached."
        )
        val server = value(request, "server").trim()
        val tool = value(request, "tool").trim()
        if (server.isBlank() || tool.isBlank()) {
            return HttpResponse.badRequest(
                "MCP call needs 'server' and 'tool'.",
                "POST /v1/mcp/call?server=<name>&tool=<tool>&callerId=<id>&paths=<path>&arguments=<json>"
            )
        }
        val callerId = value(request, "callerId").trim()
        val paths = value(request, "paths")
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
        val operation = value(request, "operation").trim().ifBlank { tool }
        if (callerId.isBlank() || paths.isEmpty()) {
            return HttpResponse.badRequest(
                "MCP call needs 'callerId' and a comma-separated 'paths' territory.",
                "POST /v1/mcp/call?server=<name>&tool=<tool>&callerId=<id>&paths=<path>"
            )
        }
        val gate = mcpBridge.judge(
            InboundToolRequest(
                source = InboundSource.MCP,
                callerId = callerId,
                operation = operation,
                paths = paths,
                territoryGrantId = value(request, "territoryGrantId").trim().ifBlank { null }
            )
        )
        when (gate) {
            is InboundGateResult.Refused -> return HttpResponse.refusal(
                403,
                "mcp-call-refused",
                gate.reason,
                "Use an exposed operation and a declared territory."
            )
            is InboundGateResult.Judged -> if (gate.decision.disposition.name != "ALLOWED") {
                return HttpResponse.refusal(
                    403,
                    "mcp-call-refused",
                    gate.decision.reason,
                    "Obtain the required approval or narrow the MCP territory."
                )
            }
        }
        return runCatching {
            val result = manager.callTool(
                serverName = server,
                toolName = tool,
                argumentsJson = value(request, "arguments").ifBlank { "{}" },
                callerId = callerId,
                operation = operation,
                territoryPaths = paths
            )
            HttpResponse.json(
                JsonWriter.obj(
                    "ok" to JsonWriter.bool(true),
                    "server" to JsonWriter.str(server),
                    "tool" to JsonWriter.str(tool),
                    "response" to JsonWriter.str(result.response),
                    "evidenceSha256" to JsonWriter.str(result.evidence.sha256.orEmpty()),
                    "evidencePath" to JsonWriter.str(result.evidence.path?.toString().orEmpty()),
                    "noEvidenceReason" to JsonWriter.str(result.evidence.noEvidenceReason.orEmpty())
                )
            )
        }.getOrElse { failure ->
            HttpResponse.refusal(
                403,
                "mcp-call-refused",
                failure.message ?: "MCP tool call refused",
                "Check /v1/mcp/status and the local allowlist before retrying."
            )
        }
    }

    fun status(): HttpResponse = host?.let { manager ->
        HttpResponse.json(JsonWriter.arr(manager.statuses().map { status ->
            JsonWriter.obj(
                "name" to JsonWriter.str(status.server.name),
                "transport" to JsonWriter.str(status.server.transport),
                "health" to JsonWriter.str(status.health.name.lowercase()),
                "reason" to JsonWriter.str(status.reason)
            )
        }))
    } ?: HttpResponse.refusal(
        501,
        "mcp-host-not-wired",
        "This bridge build has no MCP host bound.",
        "Start the engine normally so the local mcp.json host is attached."
    )

    private fun value(request: atropos.bridge.http.HttpRequest, key: String): String =
        request.query[key]?.takeIf(String::isNotBlank)
            ?: request.body.split('&')
                .firstOrNull { it.substringBefore('=') == key }
                ?.substringAfter('=', "")
            ?: ""
}
