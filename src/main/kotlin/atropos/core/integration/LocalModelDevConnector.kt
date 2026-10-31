/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.net.HttpURLConnection
import java.net.URI

/**
 * ATOM AI4: LOCAL MODEL PROTOCOL DEV CONNECTOR
 * Path: src/main/kotlin/atropos/core/integration/LocalModelDevConnector.kt
 *
 * Connects Atropos to local model backends (Ollama, LM Studio, vLLM)
 * via standard OpenAI-compatible and MCP REST/JSON-RPC protocols.
 */
data class LocalModelEndpoint(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val isAvailable: Boolean = true
)

class LocalModelDevConnector(
    private val defaultBaseUrl: String = "http://localhost:11434"
) {
    fun probeAvailability(endpoint: LocalModelEndpoint): Boolean {
        return try {
            val url = URI("${endpoint.baseUrl}/v1/models").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 1000
            conn.readTimeout = 1000
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }

    fun listRegisteredEndpoints(): List<LocalModelEndpoint> {
        return listOf(
            LocalModelEndpoint("local-ollama", "Local Ollama Engine", "http://localhost:11434", "qwen2.5-coder:7b"),
            LocalModelEndpoint("local-lmstudio", "Local LM Studio Engine", "http://localhost:1234", "hermes-3-llama-3.1-8b"),
            LocalModelEndpoint("local-vllm", "Local vLLM Server", "http://localhost:8000", "deepseek-coder-6.7b")
        )
    }

    companion object {
        val defaultConnector = LocalModelDevConnector()
    }
}
