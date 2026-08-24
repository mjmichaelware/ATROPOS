package atropos.core

import atropos.core.security.RedactionFilter
import atropos.core.provider.adapter.ProviderAdapterAiBridge
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface AIProvider {
    val name: String
    fun complete(prompt: String, context: String = ""): String

    /** Optional generation controls; legacy providers retain their old entry point. */
    fun complete(prompt: String, context: String, parameters: GenerationParameters): String =
        complete(prompt, context)
}

data class GenerationParameters(
    val temperature: Double = 0.1,
    val topP: Double = 0.95,
    val fewShotExamples: List<String> = emptyList()
)

class ProviderFactory(private val config: AtroposConfig = AtroposConfig.load()) {
    private val canonicalAdapters by lazy {
        StaticProviderAdapterRegistry(env = providerEnvironment(config))
    }

    fun getProvider(name: String = config.runtime.defaultProvider): AIProvider {
        val providerId = name.trim().lowercase()
        return when (providerId) {
            "groq" -> GroqProvider(config.keys.groq)
            "openai" -> OpenAiProvider(config.keys.openai)
            "anthropic" -> AnthropicProvider(config.keys.anthropic)
            "xai" -> XAiProvider(config.keys.xai)
            "github_models" -> GitHubModelsProvider()
            "cloudflare_ai" -> CloudflareAiProvider()
            "sambanova" -> SambaNovaProvider()
            "deepseek_direct" -> DeepSeekDirectProvider()
            "ollama" -> OllamaProvider()
            else -> canonicalAdapters.getByProviderId(providerId)
                ?.takeIf { it.status().implemented }
                ?.let(::ProviderAdapterAiBridge)
                ?: throw IllegalArgumentException("Unsupported provider: $name")
        }
    }

    private fun providerEnvironment(config: AtroposConfig): Map<String, String> = buildMap {
        putAll(System.getenv())
        config.keys.groq.takeIf { it.isNotBlank() }?.let { put("GROQ_API_KEY", it) }
        config.keys.openai.takeIf { it.isNotBlank() }?.let { put("OPENAI_API_KEY", it) }
        config.keys.anthropic.takeIf { it.isNotBlank() }?.let { put("ANTHROPIC_API_KEY", it) }
        config.keys.xai.takeIf { it.isNotBlank() }?.let { put("XAI_API_KEY", it) }
    }
}

abstract class BaseHttpProvider : AIProvider {
    protected val redactionFilter: RedactionFilter = RedactionFilter()

    protected val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(45))
        .build()

    private val generationParameters = ThreadLocal.withInitial { GenerationParameters() }

    final override fun complete(
        prompt: String,
        context: String,
        parameters: GenerationParameters
    ): String {
        val previous = generationParameters.get()
        generationParameters.set(parameters)
        return try {
            complete(prompt, context)
        } finally {
            generationParameters.set(previous)
        }
    }

    protected fun currentGenerationParameters(): GenerationParameters = generationParameters.get()

    protected fun jsonEscape(input: String): String {
        val out = StringBuilder(input.length + 16)
        for (ch in input) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> {}
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    protected fun requireKey(token: String?, providerName: String): String {
        if (token.isNullOrBlank()) throw IllegalStateException("$providerName API key is missing.")
        return token
    }

    protected fun postJson(
        uri: String,
        payload: String,
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds((System.getenv("ATROPOS_HTTP_TIMEOUT_SECONDS") ?: "240").toLongOrNull() ?: 240L))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))

        if (!bearerToken.isNullOrBlank()) builder.header("Authorization", "Bearer $bearerToken")
        for ((k, v) in extraHeaders) builder.header(k, v)

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
        val bodyBytes = response.body()
        require(bodyBytes.size <= MAX_RESPONSE_BYTES) {
            "provider response exceeded $MAX_RESPONSE_BYTES bytes"
        }
        val body = bodyBytes.toString(Charsets.UTF_8)
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()} :: ${redactionFilter.redact(body)}")
        }
        return body
    }

    protected fun postOpenAiCompatibleChat(
        uri: String,
        model: String,
        prompt: String,
        context: String = "",
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        temperature: Double? = null
    ): String {
        val generation = currentGenerationParameters()
        val payload = buildOpenAiCompatiblePayload(
            model,
            prompt,
            context,
            temperature ?: generation.temperature,
            generation.topP
        )
        val raw = postJson(uri, payload, bearerToken = bearerToken, extraHeaders = extraHeaders)
        return extractOpenAiChatContent(raw) ?: raw.trim()
    }

    protected fun buildOpenAiCompatiblePayload(
        model: String,
        prompt: String,
        context: String = "",
        temperature: Double = 0.1,
        topP: Double = 0.95
    ): String = buildString {
        append("{")
        append("\"model\":\"").append(jsonEscape(model)).append("\",")
        append("\"messages\":[")
        val system = context.trim()
        var first = true
        if (system.isNotBlank()) {
            append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(system)).append("\"}")
            first = false
        }
        if (!first) append(",")
        append("{\"role\":\"user\",\"content\":\"").append(jsonEscape(prompt.trim())).append("\"}")
        append("],")
        append("\"temperature\":").append(temperature).append(",")
        append("\"top_p\":").append(topP)
        append("}")
    }

    protected fun extractOpenAiChatContent(raw: String): String? {
        val choicesIndex = raw.indexOf("\"choices\"")
        if (choicesIndex < 0) return null
        val messageIndex = raw.indexOf("\"message\"", choicesIndex)
        if (messageIndex < 0) return null
        val contentIndex = raw.indexOf("\"content\"", messageIndex)
        if (contentIndex < 0) return null
        return extractQuotedJsonString(raw, contentIndex + "\"content\"".length)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    protected fun extractAnthropicText(raw: String): String? {
        val contentIndex = raw.indexOf("\"content\"")
        if (contentIndex < 0) return null
        val textIndex = raw.indexOf("\"text\"", contentIndex)
        if (textIndex < 0) return null
        return extractQuotedJsonString(raw, textIndex + "\"text\"".length)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    }

    private fun extractQuotedJsonString(raw: String, afterKeyIndex: Int): String? {
        var index = afterKeyIndex
        while (index < raw.length && raw[index].isWhitespace()) index++
        if (index >= raw.length || raw[index] != ':') return null
        index++
        while (index < raw.length && raw[index].isWhitespace()) index++
        if (index >= raw.length || raw[index] != '"') return null
        index++

        val out = StringBuilder()
        var escaped = false
        while (index < raw.length) {
            val ch = raw[index]
            if (escaped) {
                when (ch) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        if (index + 4 < raw.length) {
                            val hex = raw.substring(index + 1, index + 5)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                out.append(code.toChar())
                                index += 5
                                escaped = false
                                continue
                            }
                        }
                        out.append('u')
                    }
                    else -> out.append(ch)
                }
                escaped = false
            } else {
                when (ch) {
                    '\\' -> escaped = true
                    '"' -> return out.toString()
                    else -> out.append(ch)
                }
            }
            index++
        }

        return null
    }

    protected fun unescapeJsonString(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0

        while (index < value.length) {
            val ch = value[index]

            if (ch != '\\' || index + 1 >= value.length) {
                out.append(ch)
                index++
                continue
            }

            when (val escaped = value[index + 1]) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (index + 5 < value.length) {
                        val hex = value.substring(index + 2, index + 6)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            out.append(code.toChar())
                            index += 6
                            continue
                        }
                    }
                    out.append("\\u")
                }
                else -> out.append(escaped)
            }

            index += 2
        }

        return out.toString()
    }
}
