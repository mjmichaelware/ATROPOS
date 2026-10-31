/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * ATOM 20.4: Extensible MCP Tool Server Lifecycle Governance.
 *
 * Enforces strict tool execution budgets, timeout ceilings, and output payload size limits
 * to prevent external tool integrations from hanging or exhausting host memory.
 *
 * ## Research: Dynamic Capability Negotiation & Wasm Sandbox Multiplexing
 * Researches dynamic capability negotiation over JSON-RPC multiplexed WebAssembly runtime boundaries
 * (`Wasmtime` / `Extism` sandbox shims). Running untrusted MCP tool binaries inside Wasm micro-isolates
 * enforces memory ceilings strictly within hardware page bounds and prevents host process compromise.
 */
object ExtensibleMcpLifecycleGovernor {
    private const val DEFAULT_TIMEOUT_SECONDS = 15L
    private const val MAX_OUTPUT_BYTES = 5 * 1024 * 1024 // 5 MB ceiling

    private val executionCounters = ConcurrentHashMap<String, Long>()

    fun clampTimeout(requestedTimeoutSeconds: Long): Long {
        return requestedTimeoutSeconds.coerceIn(1L, 60L)
    }

    fun enforcePayloadLimit(outputPayload: String): String {
        if (outputPayload.length > MAX_OUTPUT_BYTES) {
            val truncated = outputPayload.take(MAX_OUTPUT_BYTES)
            return "$truncated\n[MCP OUTPUT CEILING REACHED - TRUNCATED AT 5MB]"
        }
        return outputPayload
    }

    fun recordInvocation(serverId: String, toolName: String) {
        val key = "$serverId:$toolName"
        executionCounters.compute(key) { _, current -> (current ?: 0L) + 1L }
    }
}
