/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.supremacy.AirGapViolationException
import atropos.core.supremacy.OpenCoreAirGapController

/**
 * ADD-MCP-008: Local-Only Remote Transport Interceptor
 * Section 2.1 mandate: When local-only or air-gapped mode is active, remote MCP transports
 * (HTTP, SSE, WebSockets) are hard-severed. Only local stdio transports are permitted.
 */

enum class TransportType { LOCAL_STDIO, REMOTE_HTTP, REMOTE_SSE, REMOTE_WEBSOCKET }

data class TransportValidationResult(
    val transportType: TransportType,
    val isPermitted: Boolean,
    val violationReason: String?
)

class McpTransportSecurityInterceptor(
    val airGapController: OpenCoreAirGapController = OpenCoreAirGapController
) {
    fun resolveTransportType(transport: String): TransportType {
        return when (transport.lowercase().trim()) {
            "stdio", "process", "pipe" -> TransportType.LOCAL_STDIO
            "http", "streamable-http" -> TransportType.REMOTE_HTTP
            "sse" -> TransportType.REMOTE_SSE
            "ws", "websocket" -> TransportType.REMOTE_WEBSOCKET
            else -> if (transport.startsWith("http")) TransportType.REMOTE_HTTP else TransportType.LOCAL_STDIO
        }
    }

    fun validateServerTransport(config: McpServerConfig): TransportValidationResult {
        val tType = resolveTransportType(config.transport)
        val mode = airGapController.currentMode()

        if (mode.isLocalOnly || mode.airGapped) {
            if (tType != TransportType.LOCAL_STDIO) {
                return TransportValidationResult(
                    transportType = tType,
                    isPermitted = false,
                    violationReason = "Remote transport [$tType] is forbidden under sovereign local-only policy"
                )
            }
            if (config.url != null && !config.url.contains("127.0.0.1") && !config.url.contains("localhost")) {
                return TransportValidationResult(
                    transportType = tType,
                    isPermitted = false,
                    violationReason = "Outbound remote URL [${config.url}] is forbidden under air-gap policy"
                )
            }
        }

        return TransportValidationResult(
            transportType = tType,
            isPermitted = true,
            violationReason = null
        )
    }

    fun assertTransportPermitted(config: McpServerConfig) {
        val validation = validateServerTransport(config)
        if (!validation.isPermitted) {
            throw AirGapViolationException(
                validation.violationReason ?: "Transport violation detected for server [${config.name}]"
            )
        }
    }
}
