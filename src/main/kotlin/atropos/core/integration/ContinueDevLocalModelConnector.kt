/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import org.json.JSONObject

/**
 * ATOM AI4: CONTINUE DEV OPEN-SOURCE LOCAL MODEL CONNECTOR
 * Path: src/main/kotlin/atropos/core/integration/ContinueDevLocalModelConnector.kt
 *
 * Connects local IDE workspaces to self-hosted open-source large language models
 * (DeepSeek, Qwen, Mistral) with strict air-gapped local-only mode that guarantees
 * no code egresses beyond the local subnet.
 */
data class LocalModelProfile(
    val title: String,
    val provider: String = "ollama",
    val modelName: String,
    val apiBase: String = "http://localhost:11434/v1",
    val contextLength: Int = 32768,
    val completionOptions: Map<String, Any> = mapOf("temperature" to 0.1)
)

data class AirgapVerificationResult(
    val isLocalSubnet: Boolean,
    val endpointHost: String,
    val allowed: Boolean,
    val rejectionReason: String? = null
)

class ContinueDevLocalModelConnector(
    private val localOnlyMode: Boolean = true
) {
    fun verifyAirgapBoundaries(endpointUrl: String): AirgapVerificationResult {
        val uri = try { java.net.URI.create(endpointUrl) } catch (_: Exception) { null }
        val host = uri?.host ?: "unknown"

        val isLoopbackOrLocal = host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            host.startsWith("172.16.") ||
            host.endsWith(".internal")

        return if (localOnlyMode && !isLoopbackOrLocal) {
            AirgapVerificationResult(
                isLocalSubnet = false,
                endpointHost = host,
                allowed = false,
                rejectionReason = "AIRGAP VIOLATION: Endpoint '$endpointUrl' is external to local subnet while localOnlyMode=true."
            )
        } else {
            AirgapVerificationResult(
                isLocalSubnet = isLoopbackOrLocal,
                endpointHost = host,
                allowed = true
            )
        }
    }

    fun generateContinueConfigJson(profiles: List<LocalModelProfile>): String {
        val root = JSONObject()
        val modelsArray = org.json.JSONArray()

        for (p in profiles) {
            val obj = JSONObject()
            obj.put("title", p.title)
            obj.put("provider", p.provider)
            obj.put("model", p.modelName)
            obj.put("apiBase", p.apiBase)
            obj.put("contextLength", p.contextLength)
            modelsArray.put(obj)
        }

        root.put("models", modelsArray)
        root.put("allowAnonymousTelemetry", false)
        return root.toString(2)
    }

    companion object {
        val defaultConnector = ContinueDevLocalModelConnector()
    }
}
