/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.integration.InboundGateResult
import atropos.core.integration.InboundSource
import atropos.core.integration.InboundToolRequest
import atropos.core.security.RedactionFilter

/**
 * Wire-format adapter shared by the external proposal surfaces.
 *
 * MCP and computer-use differ in admission policy, but neither needs a second
 * parser or response contract. The supplied judge remains the canonical gate
 * owner; this class only decodes bounded request fields and projects the result.
 */
internal class BridgeInboundToolHandler(
    private val source: InboundSource,
    private val judge: (InboundToolRequest) -> InboundGateResult,
    private val surfaceName: String,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun judge(request: HttpRequest): HttpResponse {
        val callerId = request.query["callerId"].orEmpty().ifBlank { field(request.body, "callerId") }
        val operation = request.query["operation"].orEmpty().ifBlank { field(request.body, "operation") }
        val pathsRaw = request.query["paths"].orEmpty().ifBlank { field(request.body, "paths") }
        val paths = pathsRaw.split(",").map(String::trim).filter(String::isNotBlank)
        val targetSurface = request.query["targetSurface"].orEmpty()
            .ifBlank { field(request.body, "targetSurface") }.takeIf(String::isNotBlank)
        val territoryGrantId = request.query["territoryGrantId"].orEmpty()
            .ifBlank { field(request.body, "territoryGrantId") }.takeIf(String::isNotBlank)

        if (callerId.isBlank() || operation.isBlank() || paths.isEmpty()) {
            return HttpResponse.badRequest(
                "A $surfaceName judge request needs 'callerId', 'operation', and a comma-separated 'paths' list.",
                "POST /v1/${surfaceName.lowercase()}/judge?callerId=<id>&operation=<op>&paths=<p1,p2>"
            )
        }

        val inbound = InboundToolRequest(
            source = source,
            callerId = callerId,
            operation = operation,
            paths = paths,
            targetSurface = targetSurface,
            territoryGrantId = territoryGrantId
        )
        return when (val result = judge(inbound)) {
            is InboundGateResult.Judged -> HttpResponse.json(
                JsonWriter.obj(
                    "ok" to JsonWriter.bool(true),
                    "disposition" to JsonWriter.str(result.decision.disposition.name),
                    "allowed" to JsonWriter.bool(result.decision.disposition.name == "ALLOWED"),
                    "reason" to JsonWriter.str(redactionFilter.redact(result.decision.reason))
                )
            )
            is InboundGateResult.Refused -> HttpResponse.refusal(
                400,
                "${surfaceName.lowercase()}-refused",
                redactionFilter.compact(result.reason),
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
