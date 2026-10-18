/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.security.RedactionFilter
import atropos.core.security.TokenIsolationVault
import atropos.core.security.CredentialBootstrap
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog
import atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale

enum class ProviderNativeSchema {
    OPENAI_COMPATIBLE,
    OPENROUTER,
    GEMINI_REST,
    ANTHROPIC_MESSAGES,
    CLOUDFLARE_AI,
    OLLAMA_GENERATE
}

data class NativeProviderRequest(
    val providerId: String,
    val model: String,
    val endpoint: String,
    val headers: Map<String, String>,
    val jsonPayload: String,
    val schemaType: ProviderNativeSchema
)

sealed class UniversalExecutionResult {
    data class Success(
        val providerId: String,
        val text: String,
        val rawResponse: String,
        val durationMs: Long,
        val statusCode: Int = 200,
        val headers: Map<String, List<String>> = emptyMap()
    ) : UniversalExecutionResult()

    data class Failure(
        val providerId: String,
        val statusCode: Int,
        val message: String,
        val headers: Map<String, List<String>> = emptyMap(),
        val rawBody: String = "",
        val isRateLimit: Boolean = false,
        val cause: Exception? = null
    ) : UniversalExecutionResult()
}

/**
 * Universal Request Adapter for per-provider payload translation and native execution.
 * Translates outgoing repair prompts and chat payloads into the exact native schema,
 * role hierarchy, and parameter structure expected by each active AI provider.
 */
object UniversalRequestAdapter {
    private val redactionFilter = RedactionFilter()

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    fun detectNativeSchema(providerId: String): ProviderNativeSchema {
        val clean = providerId.trim().lowercase(Locale.US)
        return when {
            clean == "openrouter" -> ProviderNativeSchema.OPENROUTER
            clean.contains("gemini") -> ProviderNativeSchema.GEMINI_REST
            clean.contains("anthropic") || clean.contains("claude") -> ProviderNativeSchema.ANTHROPIC_MESSAGES
            clean.contains("cloudflare") -> ProviderNativeSchema.CLOUDFLARE_AI
            clean.contains("ollama") -> ProviderNativeSchema.OLLAMA_GENERATE
            else -> ProviderNativeSchema.OPENAI_COMPATIBLE
        }
    }

    fun resolveModel(providerId: String, explicitModel: String? = null): String {
        if (!explicitModel.isNullOrBlank()) return explicitModel
        val clean = providerId.trim().lowercase(Locale.US)

        val live = ModelDiscoveryEngine.getLiveModels(clean)
        if (live.isNotEmpty()) {
            return live.first()
        }

        val openAiSpec = OpenAiCompatibleProviderCatalog.all().firstOrNull { it.providerId == clean }
        if (openAiSpec != null) {
            return ModelDiscoveryEngine.resolveModel(clean, openAiSpec.defaultModel)
        }
        val nonOpenAiSpec = NonOpenAiFreeProviderCatalog.get(clean)
        if (nonOpenAiSpec != null) {
            return ModelDiscoveryEngine.resolveModel(clean, nonOpenAiSpec.defaultModel)
        }

        return when {
            clean == "groq" -> ModelDiscoveryEngine.resolveModel(clean, "llama-3.3-70b-versatile")
            clean == "openrouter" -> ModelDiscoveryEngine.resolveModel(clean, "meta-llama/llama-3.1-8b-instruct:free")
            clean.contains("gemini") -> ModelDiscoveryEngine.resolveModel(clean, "gemini-1.5-flash")
            clean.contains("anthropic") -> ModelDiscoveryEngine.resolveModel(clean, "claude-3-5-sonnet-latest")
            clean == "openai" -> ModelDiscoveryEngine.resolveModel(clean, "gpt-4o-mini")
            clean == "xai" -> "grok-2-latest"
            clean == "sambanova" -> "Meta-Llama-3.3-70B-Instruct"
            clean == "deepseek_direct" -> "deepseek-chat"
            clean == "ollama" -> System.getenv("OLLAMA_MODEL") ?: "llama3.2:1b"
            else -> ProviderModelResolver.DEFAULT.resolve(clean, "default-model")
        }
    }

    fun resolveApiKey(providerId: String): String? {
        val clean = providerId.trim().lowercase(Locale.US)
        FallbackChainRegistry.getActiveKey(clean)?.takeIf { it.isNotBlank() }?.let { return it }

        val envKeys = when (clean) {
            "groq" -> listOf("GROQ_API_KEY")
            "openrouter" -> listOf("OPENROUTER_API_KEY")
            "gemini" -> listOf("GEMINI_API_KEY", "GOOGLE_API_KEY")
            "anthropic" -> listOf("ANTHROPIC_API_KEY")
            "openai" -> listOf("OPENAI_API_KEY")
            "xai" -> listOf("XAI_API_KEY")
            "github_models" -> listOf("GITHUB_MODELS_TOKEN")
            "cloudflare_ai" -> listOf("CLOUDFLARE_API_TOKEN")
            "sambanova" -> listOf("SAMBANOVA_API_KEY")
            "deepseek_direct" -> listOf("DEEPSEEK_API_KEY")
            else -> listOf("${clean.uppercase(Locale.US)}_API_KEY", "${clean.uppercase(Locale.US)}_KEY")
        }
        for (k in envKeys) {
            System.getenv(k)?.takeIf { it.isNotBlank() }?.let { return it }
        }

        val config = runCatching { AtroposConfig.load() }.getOrNull()
        if (config != null) {
            val confVal = when (clean) {
                "groq" -> config.keys.groq
                "openai" -> config.keys.openai
                "anthropic" -> config.keys.anthropic
                "xai" -> config.keys.xai
                else -> null
            }
            if (!confVal.isNullOrBlank()) return confVal
        }

        val bootstrap = runCatching { CredentialBootstrap.get().getAll() }.getOrDefault(emptyMap())
        for (k in envKeys) {
            bootstrap[k]?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return runCatching { TokenIsolationVault().readSecret("${clean.uppercase(Locale.US)}_API_KEY") }.getOrNull()
    }

    fun resolveEndpoint(providerId: String, model: String, apiKey: String? = null): String {
        val clean = providerId.trim().lowercase(Locale.US)
        val openAiSpec = OpenAiCompatibleProviderCatalog.all().firstOrNull { it.providerId == clean }
        if (openAiSpec != null) {
            return openAiSpec.endpointEnv?.let { System.getenv(it) }?.takeIf { it.isNotBlank() } ?: openAiSpec.baseUrl
        }
        val nonOpenAiSpec = NonOpenAiFreeProviderCatalog.get(clean)
        if (nonOpenAiSpec != null) {
            val ep = nonOpenAiSpec.endpoint.replace("{model}", model)
            return if (clean.contains("gemini") && !apiKey.isNullOrBlank()) "$ep?key=$apiKey" else ep
        }
        return when (clean) {
            "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" + (apiKey?.let { "?key=$it" } ?: "")
            "anthropic" -> "https://api.anthropic.com/v1/messages"
            "openai" -> "https://api.openai.com/v1/chat/completions"
            "xai" -> "https://api.x.ai/v1/chat/completions"
            "github_models" -> "https://models.inference.ai.azure.com/chat/completions"
            "sambanova" -> "https://api.sambanova.ai/v1/chat/completions"
            "deepseek_direct" -> "https://api.deepseek.com/chat/completions"
            "ollama" -> (System.getenv("OLLAMA_HOST") ?: "http://127.0.0.1:11434").trimEnd('/') + "/api/generate"
            else -> "https://api.openai.com/v1/chat/completions"
        }
    }

    fun buildHeaders(providerId: String, apiKey: String?): Map<String, String> {
        val clean = providerId.trim().lowercase(Locale.US)
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"

        when (clean) {
            "openrouter" -> {
                if (!apiKey.isNullOrBlank()) headers["Authorization"] = "Bearer $apiKey"
                headers["HTTP-Referer"] = "https://local.atropos.invalid"
                headers["X-Title"] = "ATROPOS"
            }
            "anthropic" -> {
                if (!apiKey.isNullOrBlank()) headers["x-api-key"] = apiKey
                headers["anthropic-version"] = "2023-06-01"
            }
            "gemini" -> {
                if (!apiKey.isNullOrBlank()) headers["x-goog-api-key"] = apiKey
            }
            "github_models" -> {
                if (!apiKey.isNullOrBlank()) headers["Authorization"] = "Bearer $apiKey"
            }
            "azure_openai" -> {
                if (!apiKey.isNullOrBlank()) headers["api-key"] = apiKey
            }
            else -> {
                if (!apiKey.isNullOrBlank()) headers["Authorization"] = "Bearer $apiKey"
            }
        }
        return headers
    }

    fun translate(
        providerId: String,
        prompt: String,
        context: String = "",
        modelOverride: String? = null,
        temperature: Double = 0.1,
        apiKey: String? = null
    ): NativeProviderRequest {
        val clean = providerId.trim().lowercase(Locale.US)
        val schema = detectNativeSchema(clean)
        val model = resolveModel(clean, modelOverride)
        val effectiveKey = apiKey ?: resolveApiKey(clean)
        val endpoint = resolveEndpoint(clean, model, effectiveKey)
        val headers = buildHeaders(clean, effectiveKey)

        val redactedPrompt = redactionFilter.redact(prompt)
        val redactedContext = redactionFilter.redact(context)

        val jsonPayload = when (schema) {
            ProviderNativeSchema.OPENAI_COMPATIBLE -> {
                val root = JSONObject()
                root.put("model", model)
                val messages = JSONArray()
                if (redactedContext.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", redactedContext.trim()))
                }
                messages.put(JSONObject().put("role", "user").put("content", redactedPrompt.trim()))
                root.put("messages", messages)
                root.put("temperature", temperature)
                root.put("top_p", 0.95)
                root.toString()
            }
            ProviderNativeSchema.OPENROUTER -> {
                val root = JSONObject()
                root.put("model", model)
                val messages = JSONArray()
                if (redactedContext.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", redactedContext.trim()))
                }
                messages.put(JSONObject().put("role", "user").put("content", redactedPrompt.trim()))
                root.put("messages", messages)
                root.put("temperature", temperature)
                root.toString()
            }
            ProviderNativeSchema.GEMINI_REST -> {
                val root = JSONObject()
                if (redactedContext.isNotBlank()) {
                    val parts = JSONArray().put(JSONObject().put("text", redactedContext.trim()))
                    root.put("systemInstruction", JSONObject().put("parts", parts))
                }
                val userParts = JSONArray().put(JSONObject().put("text", redactedPrompt.trim()))
                val userContent = JSONObject().put("role", "user").put("parts", userParts)
                root.put("contents", JSONArray().put(userContent))
                root.put("generationConfig", JSONObject().put("temperature", temperature))
                root.toString()
            }
            ProviderNativeSchema.ANTHROPIC_MESSAGES -> {
                val root = JSONObject()
                root.put("model", model)
                root.put("max_tokens", 4096)
                root.put("temperature", temperature)
                if (redactedContext.isNotBlank()) {
                    root.put("system", redactedContext.trim())
                }
                val userMsg = JSONObject().put("role", "user").put("content", redactedPrompt.trim())
                root.put("messages", JSONArray().put(userMsg))
                root.toString()
            }
            ProviderNativeSchema.CLOUDFLARE_AI -> {
                val root = JSONObject()
                val messages = JSONArray()
                if (redactedContext.isNotBlank()) {
                    messages.put(JSONObject().put("role", "system").put("content", redactedContext.trim()))
                }
                messages.put(JSONObject().put("role", "user").put("content", redactedPrompt.trim()))
                root.put("messages", messages)
                root.toString()
            }
            ProviderNativeSchema.OLLAMA_GENERATE -> {
                val root = JSONObject()
                root.put("model", model)
                root.put("prompt", redactedPrompt.trim())
                if (redactedContext.isNotBlank()) {
                    root.put("system", redactedContext.trim())
                }
                root.put("stream", false)
                val opts = JSONObject().put("temperature", temperature).put("top_p", 0.95)
                root.put("options", opts)
                root.toString()
            }
        }

        return NativeProviderRequest(
            providerId = clean,
            model = model,
            endpoint = endpoint,
            headers = headers,
            jsonPayload = jsonPayload,
            schemaType = schema
        )
    }

    fun extractResponseContent(providerId: String, rawResponse: String, schema: ProviderNativeSchema): String {
        if (rawResponse.isBlank()) return ""
        return try {
            val root = JSONObject(rawResponse)
            when (schema) {
                ProviderNativeSchema.OPENAI_COMPATIBLE,
                ProviderNativeSchema.OPENROUTER -> {
                    val choices = root.optJSONArray("choices")
                    val msg = choices?.optJSONObject(0)?.optJSONObject("message")
                    msg?.optString("content")?.trim() ?: rawResponse.trim()
                }
                ProviderNativeSchema.GEMINI_REST -> {
                    val candidates = root.optJSONArray("candidates")
                    val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    parts?.optJSONObject(0)?.optString("text")?.trim() ?: rawResponse.trim()
                }
                ProviderNativeSchema.ANTHROPIC_MESSAGES -> {
                    val content = root.optJSONArray("content")
                    content?.optJSONObject(0)?.optString("text")?.trim() ?: rawResponse.trim()
                }
                ProviderNativeSchema.CLOUDFLARE_AI -> {
                    root.optJSONObject("result")?.optString("response")?.trim()
                        ?: root.optString("response").trim()
                        ?: root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                        ?: rawResponse.trim()
                }
                ProviderNativeSchema.OLLAMA_GENERATE -> {
                    root.optString("response").trim().ifBlank { rawResponse.trim() }
                }
            }
        } catch (_: Exception) {
            rawResponse.trim()
        }
    }

    fun execute(
        request: NativeProviderRequest,
        timeoutSeconds: Long = 120L
    ): UniversalExecutionResult {
        val started = System.currentTimeMillis()
        return try {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(request.endpoint))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(request.jsonPayload))

            request.headers.forEach { (k, v) -> builder.header(k, v) }

            val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            val durationMs = System.currentTimeMillis() - started
            val statusCode = response.statusCode()
            val headers = response.headers().map()
            val body = response.body().toString(Charsets.UTF_8)

            val isRateLimit = statusCode == 429 ||
                body.contains("rate limit", ignoreCase = true) ||
                body.contains("quota exceeded", ignoreCase = true) ||
                body.contains("429")

            if (statusCode in 200..299) {
                val content = extractResponseContent(request.providerId, body, request.schemaType)
                UniversalExecutionResult.Success(
                    providerId = request.providerId,
                    text = content,
                    rawResponse = body,
                    durationMs = durationMs,
                    statusCode = statusCode,
                    headers = headers
                )
            } else {
                UniversalExecutionResult.Failure(
                    providerId = request.providerId,
                    statusCode = statusCode,
                    message = "HTTP $statusCode from ${request.providerId}: ${redactionFilter.redact(body)}",
                    headers = headers,
                    rawBody = body,
                    isRateLimit = isRateLimit
                )
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - started
            val universalError = UniversalErrorParser.parseAny(e)
            val isRateLimit = universalError.category == UniversalErrorCategory.RATE_LIMIT_OVERLOAD ||
                e.message?.contains("429") == true ||
                e.message?.contains("rate limit", ignoreCase = true) == true
            UniversalExecutionResult.Failure(
                providerId = request.providerId,
                statusCode = universalError.httpCode ?: if (isRateLimit) 429 else 500,
                message = e.message ?: "Transport error",
                headers = emptyMap(),
                rawBody = universalError.raw,
                isRateLimit = isRateLimit,
                cause = e
            )
        }
    }
}
