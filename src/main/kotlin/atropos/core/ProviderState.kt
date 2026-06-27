/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeoutException

enum class FailureType {
    MISSING_KEY,
    AUTH_INVALID,
    RATE_LIMIT,
    CONNECTION_REFUSED,
    TIMEOUT,
    MODEL_MISSING,
    BAD_JSON,
    UNKNOWN
}

data class ProviderError(
    val provider: String,
    val type: FailureType,
    val cleanMessage: String,
    val rawMessage: String = ""
)

data class OllamaStatus(
    val online: Boolean,
    val models: List<String>,
    val selectedModel: String,
    val host: String
)

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

class ProviderFailureClassifier {
    fun classify(providerName: String, failure: Exception): ProviderError {
        val provider = providerName.lowercase()
        val message = failure.message ?: failure.javaClass.simpleName
        val lower = message.lowercase()
        val debug = System.getenv("ATROPOS_DEBUG_ERRORS") == "true"
        val raw = if (debug) message else ""

        return when {
            lower.contains("credential bindings are empty") ||
                lower.contains("api key is missing") ||
                lower.contains("missing api key") ->
                ProviderError(
                    provider,
                    FailureType.MISSING_KEY,
                    "$provider auth failed: missing API key",
                    raw
                )

            lower.contains("http 401") ||
                lower.contains("invalid api key") ||
                lower.contains("invalid_api_key") ->
                ProviderError(
                    provider,
                    FailureType.AUTH_INVALID,
                    "$provider auth failed: invalid API key",
                    raw
                )

            lower.contains("http 429") ||
                lower.contains("rate limit") ->
                ProviderError(
                    provider,
                    FailureType.RATE_LIMIT,
                    "$provider rate limit exceeded",
                    raw
                )

            failure is ConnectException ||
                lower.contains("connection refused") ||
                lower.contains("connectexception") ->
                ProviderError(
                    provider,
                    FailureType.CONNECTION_REFUSED,
                    if (provider == "ollama") {
                        "ollama unavailable at ${OllamaHealthProbe.defaultHost()}"
                    } else {
                        "$provider unavailable"
                    },
                    raw
                )

            failure is TimeoutException ||
                lower.contains("timeout") ||
                lower.contains("timed out") ->
                ProviderError(
                    provider,
                    FailureType.TIMEOUT,
                    "$provider connection timed out",
                    raw
                )

            lower.contains("model") &&
                (
                    lower.contains("not found") ||
                        lower.contains("not installed") ||
                        lower.contains("missing")
                    ) ->
                ProviderError(
                    provider,
                    FailureType.MODEL_MISSING,
                    "$provider model missing",
                    raw
                )

            lower.contains("json") ||
                lower.contains("parse") ->
                ProviderError(
                    provider,
                    FailureType.BAD_JSON,
                    "$provider returned malformed JSON",
                    raw
                )

            else ->
                ProviderError(
                    provider,
                    FailureType.UNKNOWN,
                    "$provider execution failed",
                    raw
                )
        }
    }
}

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
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create("${host.trimEnd('/')}/api/tags"))
                .timeout(Duration.ofMillis(1500))
                .GET()
                .build()

            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            )

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
            ).findAll(response.body())
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
    }
}

data class ProviderCascadeResult(
    val providerName: String,
    val response: String,
    val errors: List<ProviderError>
)

class ProviderCascadeRouter(
    private val factory: ProviderFactory,
    private val classifier: ProviderFailureClassifier =
        ProviderFailureClassifier()
) {
    fun completeWithCascade(
        requestedProvider: String,
        prompt: String,
        context: String,
        onFailure: (ProviderError) -> Unit = {}
    ): ProviderCascadeResult {
        val ollamaStatus = OllamaHealthProbe().probe()
        val order = providerOrder(requestedProvider)
        val errors = mutableListOf<ProviderError>()
        val blocked = mutableSetOf<String>()

        for (providerName in order) {
            val provider = providerName.lowercase()
            if (provider in blocked) continue

            if (provider == "ollama" && !ollamaStatus.online) {
                val error = ProviderError(
                    provider = "ollama",
                    type = FailureType.CONNECTION_REFUSED,
                    cleanMessage = "ollama unavailable at ${ollamaStatus.host}"
                )
                errors += error
                onFailure(error)
                continue
            }

            try {
                val aiProvider = factory.getProvider(provider)
                val response = aiProvider.complete(prompt, context)

                return ProviderCascadeResult(
                    providerName = provider,
                    response = response,
                    errors = errors
                )
            } catch (failure: Exception) {
                val error = classifier.classify(provider, failure)
                errors += error
                onFailure(error)

                if (
                    error.type == FailureType.AUTH_INVALID ||
                    error.type == FailureType.MISSING_KEY
                ) {
                    blocked += provider
                }
            }
        }

        val cleanAggregate =
            if (errors.isEmpty()) {
                "no provider completed the request"
            } else {
                errors.joinToString(" | ") { it.cleanMessage }
            }

        throw RuntimeException(cleanAggregate)
    }

    private fun providerOrder(requestedProvider: String): List<String> {
        val configured = System.getenv("ATROPOS_PROVIDER_ORDER")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?: listOf("groq", "openai", "anthropic", "xai", "ollama")

        return (listOf(requestedProvider.lowercase()) + configured)
            .filter {
                it in setOf(
                    "groq",
                    "openai",
                    "anthropic",
                    "xai",
                    "ollama"
                )
            }
            .distinct()
    }
}
