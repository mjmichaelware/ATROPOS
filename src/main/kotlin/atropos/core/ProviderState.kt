/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ProviderRuntimeState(
    var requestedProvider: String,
    var activeProvider: String,
    var lastSuccessfulProvider: String? = null,
    var fallbackProvider: String? = null,
    var availableProviders: List<String> = emptyList(),
    var unavailableProviders: List<String> = emptyList(),
    var lastFailureType: FailureType? = null,
    var lastFailureMessage: String? = null,
    var ollamaStatus: OllamaStatus = OllamaHealthProbe().probe(),
    var fallbackEnabled: Boolean = true
)

class OllamaHealthProbe(
    private val host: String = defaultHost()
) {
    companion object {
        fun defaultHost(): String =
            (System.getenv("OLLAMA_HOST") ?: "http://127.0.0.1:11434")
                .trimEnd('/')
    }

    fun probe(): OllamaStatus {
        val requestedModel =
            (System.getenv("OLLAMA_MODEL") ?: "llama3.2:1b").trim()

        return try {
            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(800))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${host.trimEnd('/')}/api/tags"))
                .timeout(Duration.ofMillis(1500))
                .GET()
                .build()

            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
            )

            val body = response.body().use { input ->
                input.readNBytes(MAX_RESPONSE_BYTES + 1).also {
                    require(it.size <= MAX_RESPONSE_BYTES)
                }.toString(Charsets.UTF_8)
            }

            if (response.statusCode() !in 200..299) {
                return OllamaStatus(
                    online = false,
                    models = emptyList(),
                    selectedModel = requestedModel,
                    host = host.trimEnd('/')
                )
            }

            val models = Regex(
                """"name"\s*:\s*"([^"]+)""""
            ).findAll(body)
                .map { it.groupValues[1] }
                .distinct()
                .toList()

            val selected = when {
                models.contains(requestedModel) -> requestedModel
                models.contains("llama3.2:1b") -> "llama3.2:1b"
                models.isNotEmpty() -> models.first()
                else -> requestedModel
            }

            OllamaStatus(
                online = true,
                models = models,
                selectedModel = selected,
                host = host.trimEnd('/')
            )
        } catch (_: Exception) {
            OllamaStatus(
                online = false,
                models = emptyList(),
                selectedModel = requestedModel,
                host = host.trimEnd('/')
            )
        }

        private const val MAX_RESPONSE_BYTES = 1 * 1024 * 1024
    }
}
