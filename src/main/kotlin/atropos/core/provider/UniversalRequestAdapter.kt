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
 * Integrates dynamic model discovery, Ollama 180s timeout, and dynamic token budgeting.
 */
object UniversalRequestAdapter {
    private val redactionFilter = RedactionFilter()

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    private val ollamaHttpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    fun detectNativeSchema(providerId: String): ProviderNativeSchema {
        val clean = providerId.trim().lowercase(Locale.US)
        return when {
            clean == "openrouter" -> ProviderNativeSchema.OPENROUTER
            clean.contains("gemini") -> ProviderNativeSchema.GEMINI_REST
            clean.contains("anthropic") -> ProviderNativeSchema.ANTHROPIC_MESSAGES
            clean.contains("cloudflare") -> ProviderNativeSchema.CLOUDFLARE_AI
            clean.contains("ollama") -> ProviderNativeSchema.OLLAMA_GENERATE
            else -> ProviderNativeSchema.OPENAI_COMPATIBLE
        }
    }

    fun resolveModel(providerId: String, explicitModel: String? = null): String {
        val clean = providerId.trim().lowercase(Locale.US)
        if (!explicitModel.isNullOrBlank() && !explicitModel.contains("whisper", ignoreCase = true) && explicitModel != "default") {
            return explicitModel
        }

        ModelDiscoveryEngine.getCachedModel(clean)?.let { return it }

        val live = ModelDiscoveryEngine.getLiveModels(clean)
            .filterNot { it.contains("whisper", ignoreCase = true) || it.contains("guard", ignoreCase = true) || it.contains("tts", ignoreCase = true) || it.contains("embed", ignoreCase = true) }

        if (clean == "gemini") {
            val flashCandidates = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
            for (cand in flashCandidates) if (live.any { it.equals(cand, ignoreCase = true) }) return cand
            return live.firstOrNull { it.contains("flash", ignoreCase = true) } ?: "gemini-2.5-flash"
        }

        if (clean == "groq") {
            return live.firstOrNull { it == "llama-3.3-70b-versatile" || it == "qwen-2.5-coder-32b" }
                ?: live.firstOrNull { it.contains("versatile", ignoreCase = true) || it.contains("coder", ignoreCase = true) }
                ?: "llama-3.3-70b-versatile"
        }

        if (clean == "cerebras") {
            return live.firstOrNull { it.contains("70b", ignoreCase = true) } ?: live.firstOrNull() ?: "llama3.1-70b"
        }

        if (clean == "sambanova") {
            return live.firstOrNull { it.contains("70b", ignoreCase = true) } ?: live.firstOrNull() ?: "Meta-Llama-3.1-70B-Instruct"
        }

        if (live.isNotEmpty()) {
            return live.firstOrNull { it.contains("chat", ignoreCase = true) || it.contains("instruct", ignoreCase = true) || it.contains("versatile", ignoreCase = true) }
                ?: live.first()
        }

        val openAiSpec = OpenAiCompatibleProviderCatalog.all().firstOrNull { it.providerId == clean }
        if (openAiSpec != null) {
            val defaultM = if (clean == "groq") "llama-3.3-70b-versatile" else openAiSpec.defaultModel
            return ModelDiscoveryEngine.resolveModel(clean, defaultM)
        }
        val nonOpenAiSpec = NonOpenAiFreeProviderCatalog.get(clean)
        if (nonOpenAiSpec != null) {
            val defM = if (clean == "gemini") "gemini-2.5-flash" else nonOpenAiSpec.defaultModel
            return ModelDiscoveryEngine.resolveModel(clean, defM)
        }

        return when {
            clean == "groq" -> "llama-3.3-70b-versatile"
            clean == "cerebras" -> "llama3.1-70b"
            clean == "sambanova" -> "Meta-Llama-3.1-70B-Instruct"
            clean == "openrouter" -> ModelDiscoveryEngine.resolveModel(clean, "meta-llama/llama-3.3-70b-instruct")
            clean.contains("gemini") -> "gemini-2.5-flash"
            clean.contains("anthropic") -> ModelDiscoveryEngine.resolveModel(clean, "sonnet-3-5-latest")
            clean == "openai" -> ModelDiscoveryEngine.resolveModel(clean, "gpt-4o-mini")
            clean == "deepseek_direct" || clean == "deepseek" -> "deepseek-chat"
            clean == "ollama" -> System.getenv("OLLAMA_MODEL") ?: "llama3.2:1b"
            else -> ProviderModelResolver.DEFAULT.resolve(clean, "default-model")
        }
    }

    fun resolveApiKey(providerId: String): String? {
        val clean = providerId.trim().lowercase(Locale.US)
        if (ModelDiscoveryEngine.isNonLlm(clean)) return null
        FallbackChainRegistry.getActiveKey(clean)?.takeIf { it.isNotBlank() }?.let { return it }

        val envKeys = when (clean) {
            "groq" -> listOf("GROQ_API_KEY")
            "cerebras" -> listOf("CEREBRAS_API_KEY")
            "sambanova" -> listOf("SAMBANOVA_API_KEY")
            "openrouter" -> listOf("OPENROUTER_API_KEY")
            "gemini" -> listOf("GEMINI_API_KEY", "GOOGLE_API_KEY")
            "anthropic" -> listOf("ANTHROPIC_API_KEY")
            "openai" -> listOf("OPENAI_API_KEY")
            "xai" -> listOf("XAI_API_KEY")
            "github_models" -> listOf("GITHUB_MODELS_TOKEN")
            "cloudflare_ai" -> listOf("CLOUDFLARE_API_TOKEN")
            "deepseek_direct", "deepseek" -> listOf("DEEPSEEK_API_KEY")
            "together" -> listOf("TOGETHER_API_KEY")
            "nvidia" -> listOf("NVIDIA_API_KEY")
            "mistral" -> listOf("MISTRAL_API_KEY")
            else -> listOf("${clean.uppercase(Locale.US)}_API_KEY", "${clean.uppercase(Locale.US)}_KEY")
        }
        for (k in envKeys) System.getenv(k)?.takeIf { it.isNotBlank() }?.let { return it }

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
        for (k in envKeys) bootstrap[k]?.takeIf { it.isNotBlank() }?.let { return it }

        return runCatching { TokenIsolationVault().readSecret("${clean.uppercase(Locale.US)}_API_KEY") }.getOrNull()
    }

    fun resolveEndpoint(providerId: String, model: String, apiKey: String? = null): String {
        val clean = providerId.trim().lowercase(Locale.US)
        val openAiSpec = OpenAiCompatibleProviderCatalog.all().firstOrNull { it.providerId == clean }
        if (openAiSpec != null) {
            val base = openAiSpec.endpointEnv?.let { System.getenv(it) }?.takeIf { it.isNotBlank() } ?: openAiSpec.baseUrl
            return if (base.endsWith("/chat/completions")) base else base.trimEnd('/') + "/chat/completions"
        }
        val nonOpenAiSpec = NonOpenAiFreeProviderCatalog.get(clean)
        if (nonOpenAiSpec != null) {
            val ep = nonOpenAiSpec.endpoint.replace("{model}", model)
            return if (clean.contains("gemini") && !apiKey.isNullOrBlank()) "$ep?key=$apiKey" else ep
        }
        return when (clean) {
            "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
            "sambanova" -> "https://api.sambanova.ai/v1/chat/completions"
            "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
            "gemini" -> "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" + (apiKey?.let { "?key=$it" } ?: "")
            "anthropic" -> "https://api.anthropic.com/v1/messages"
            "openai" -> "https://api.openai.com/v1/chat/completions"
            "xai" -> "https://api.x.ai/v1/chat/completions"
            "github_models" -> "https://models.inference.ai.azure.com/chat/completions"
            "deepseek_direct", "deepseek" -> "https://api.deepseek.com/chat/completions"
            "together" -> "https://api.together.xyz/v1/chat/completions"
            "nvidia" -> "https://integrate.api.nvidia.com/v1/chat/completions"
            "mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "ollama" -> (System.getenv("OLLAMA_HOST") ?: "http://127.0.0.1:11434").trimEnd('/') + "/api/generate"
            else -> "https://api.openai.com/v1/chat/completions"
        }
    }

    fun buildHeaders(providerId: String, apiKey: String?): Map<String, String> {
        val clean = providerId.trim().lowercase(Locale.US)
        val headers = mutableMapOf("Content-Type" to "application/json")
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
        apiKey: String? = null,
        maxTokens: Int? = null,
        targetFileLines: Int? = null
    ): NativeProviderRequest {
        val clean = providerId.trim().lowercase(Locale.US)
        val schema = detectNativeSchema(clean)
        val resolvedModel = resolveModel(clean, modelOverride)
        val model = if (clean == "groq" && (resolvedModel.contains("whisper", ignoreCase = true) || resolvedModel.isBlank())) {
            "llama-3.3-70b-versatile"
        } else {
            resolvedModel
        }
        val effectiveKey = apiKey ?: resolveApiKey(clean)
        val endpoint = resolveEndpoint(clean, model, effectiveKey)
        val headers = buildHeaders(clean, effectiveKey)

        val redactedPrompt = redactionFilter.redact(prompt)
        val redactedContext = redactionFilter.redact(context)
        val dynamicTokenBudget = ProviderTokenBudgeter.budgetFor(clean, maxTokens, targetFileLines)

        val jsonPayload = when (schema) {
            ProviderNativeSchema.OPENAI_COMPATIBLE -> {
                val root = JSONObject()
                root.put("model", model)
                val messages = JSONArray()
                if (redactedContext.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", redactedContext.trim()))
                messages.put(JSONObject().put("role", "user").put("content", redactedPrompt.trim()))
                root.put("messages", messages)
                root.put("temperature", temperature)
                root.put("top_p", 0.95)
                root.put("max_tokens", dynamicTokenBudget)
                root.toString()
            }
            ProviderNativeSchema.OPENROUTER -> {
                val root = JSONObject()
                root.put("model", model)
                val messages = JSONArray()
                if (redactedContext.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", redactedContext.trim()))
                messages.put(JSONObject().put("role", "user").put("content", redactedPrompt.trim()))
                root.put("messages", messages)
                root.put("temperature", temperature)
                root.put("max_tokens", dynamicTokenBudget)
                root.toString()
            }
            ProviderNativeSchema.GEMINI_REST -> {
                val root = JSONObject()
                if (redactedContext.isNotBlank()) {
                    val parts = JSONArray().put(JSONObject().put("text", redactedContext.trim()))
                    root.put("systemInstruction", JSONObject().put("parts", parts))
                }
                val userParts = JSONArray().put(JSONObject().put("text", redactedPrompt.trim()))
                root.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
                val genCfg = JSONObject().put("temperature", temperature)
                genCfg.put("maxOutputTokens", dynamicTokenBudget)
                root.put("generationConfig", genCfg)
                root.toString()
            }
            ProviderNativeSchema.ANTHROPIC_MESSAGES -> {
                val root = JSONObject()
                root.put("model", model)
                root.put("max_tokens", dynamicTokenBudget.coerceAtMost(4096))
                root.put("temperature", temperature)
                if (redactedContext.isNotBlank()) root.put("system", redactedContext.trim())
                val userMsg = JSONObject().put("role", "user").put("content", redactedPrompt.trim())
                root.put("messages", JSONArray().put(userMsg))
                root.toString()
            }
            ProviderNativeSchema.CLOUDFLARE_AI -> {
                val root = JSONObject()
                val messages = JSONArray()
                if (redactedContext.isNotBlank()) messages.put(JSONObject().put("role", "system").put("content", redactedContext.trim()))
                messages.put(JSONObject().put("role", "user").put("content", redactedPrompt.trim()))
                root.put("messages", messages)
                root.put("max_tokens", dynamicTokenBudget)
                root.toString()
            }
            ProviderNativeSchema.OLLAMA_GENERATE -> {
                val root = JSONObject()
                root.put("model", model)
                root.put("prompt", redactedPrompt.trim())
                if (redactedContext.isNotBlank()) root.put("system", redactedContext.trim())
                root.put("stream", false)
                val opts = JSONObject().put("temperature", temperature).put("top_p", 0.95)
                opts.put("num_predict", dynamicTokenBudget)
                root.put("options", opts)
                root.toString()
            }
        }

        return NativeProviderRequest(clean, model, endpoint, headers, jsonPayload, schema)
    }

    fun extractResponseContent(providerId: String, rawResponse: String, schema: ProviderNativeSchema): String {
        if (rawResponse.isBlank()) return ""
        return try {
            val root = JSONObject(rawResponse)
            when (schema) {
                ProviderNativeSchema.OPENAI_COMPATIBLE, ProviderNativeSchema.OPENROUTER -> {
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
        if (request.providerId in setOf("jina", "pinecone") || ModelDiscoveryEngine.isNonLlm(request.providerId)) {
            return UniversalExecutionResult.Failure(
                providerId = request.providerId,
                statusCode = 400,
                message = "Isolated non-chat endpoint: ${request.providerId} is an isolated non-LLM/vector API",
                headers = emptyMap(),
                rawBody = "{\"error\":\"isolated_non_chat_endpoint\"}"
            )
        }
        val isOllama = request.providerId == "ollama" || request.schemaType == ProviderNativeSchema.OLLAMA_GENERATE
        val effectiveTimeout = if (isOllama) 180L else timeoutSeconds
        val client = if (isOllama) ollamaHttpClient else httpClient
        val started = System.currentTimeMillis()

        return try {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(request.endpoint))
                .timeout(Duration.ofSeconds(effectiveTimeout))
                .POST(HttpRequest.BodyPublishers.ofString(request.jsonPayload))

            request.headers.forEach { (k, v) -> builder.header(k, v) }

            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            val durationMs = System.currentTimeMillis() - started
            val statusCode = response.statusCode()
            val headers = response.headers().map()
            val body = response.body().toString(Charsets.UTF_8)

            ProviderTokenBudgeter.recordFeedback(request.providerId, statusCode, headers, body)

            val isRateLimit = statusCode == 429 || body.contains("rate limit", ignoreCase = true) || body.contains("quota exceeded", ignoreCase = true)

            if (statusCode in 200..299) {
                val content = extractResponseContent(request.providerId, body, request.schemaType)
                UniversalExecutionResult.Success(request.providerId, content, body, durationMs, statusCode, headers)
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
            val universalError = UniversalErrorParser.parseAny(e)
            val isRateLimit = universalError.category == UniversalErrorCategory.RATE_LIMIT_OVERLOAD || e.message?.contains("429") == true
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
