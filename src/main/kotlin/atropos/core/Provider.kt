package atropos.core

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface AIProvider {
    val name: String
    fun complete(prompt: String, context: String = ""): String
}

class ProviderFactory(private val config: AtroposConfig = AtroposConfig.load()) {
    fun getProvider(name: String = config.runtime.defaultProvider): AIProvider {
        return when (name.trim().lowercase()) {
            "groq" -> GroqProvider(config.keys.groq)
            "openai" -> OpenAiProvider(config.keys.openai)
            "anthropic" -> AnthropicProvider(config.keys.anthropic)
            "xai" -> XAiProvider(config.keys.xai)
            "github_models" -> GitHubModelsProvider()
            "cloudflare_ai" -> CloudflareAiProvider()
            "sambanova" -> SambaNovaProvider()
            "deepseek_direct" -> DeepSeekDirectProvider()
            "ollama" -> OllamaProvider()
            else -> throw IllegalArgumentException("Unsupported provider: $name")
        }
    }
}

abstract class BaseHttpProvider : AIProvider {
    protected val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(45))
        .build()

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

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()} :: ${response.body()}")
        }
        return response.body()
    }

    protected fun postOpenAiCompatibleChat(
        uri: String,
        model: String,
        prompt: String,
        context: String = "",
        bearerToken: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        temperature: Double = 0.1
    ): String {
        val payload = buildOpenAiCompatiblePayload(model, prompt, context, temperature)
        val raw = postJson(uri, payload, bearerToken = bearerToken, extraHeaders = extraHeaders)
        return extractOpenAiChatContent(raw) ?: raw.trim()
    }

    protected fun buildOpenAiCompatiblePayload(
        model: String,
        prompt: String,
        context: String = "",
        temperature: Double = 0.1
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
        append("\"temperature\":").append(temperature)
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

class GroqProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "groq"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.groq.com/openai/v1/chat/completions",
            model = "llama-3.3-70b-versatile",
            prompt = prompt,
            context = context,
            bearerToken = token
        )
    }
}

class OpenAiProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "openai"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.openai.com/v1/chat/completions",
            model = "gpt-4o-mini",
            prompt = prompt,
            context = context,
            bearerToken = token
        )
    }
}

class AnthropicProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "anthropic"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        val payload = buildString {
            append("{")
            append("\"model\":\"claude-3-5-sonnet-latest\",")
            append("\"max_tokens\":4096,")
            if (context.isNotBlank()) {
                append("\"system\":\"").append(jsonEscape(context.trim())).append("\",")
            }
            append("\"messages\":[{\"role\":\"user\",\"content\":\"")
            append(jsonEscape(prompt.trim()))
            append("\"}]}")
        }

        val raw = postJson(
            "https://api.anthropic.com/v1/messages",
            payload,
            extraHeaders = mapOf(
                "x-api-key" to token,
                "anthropic-version" to "2023-06-01"
            )
        )
        return extractAnthropicText(raw) ?: raw.trim()
    }
}

class XAiProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "xai"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.x.ai/v1/chat/completions",
            model = "grok-2-latest",
            prompt = prompt,
            context = context,
            bearerToken = token
        )
    }
}

class GitHubModelsProvider(
    private val token: String? = System.getenv("GITHUB_MODELS_TOKEN")
) : BaseHttpProvider() {
    override val name = "github_models"
    override fun complete(prompt: String, context: String): String {
        val apiKey = requireKey(token, name)
        return postOpenAiCompatibleChat(
            uri = "https://models.inference.ai.azure.com/chat/completions",
            model = "gpt-4o-mini",
            prompt = prompt,
            context = context,
            bearerToken = apiKey
        )
    }
}

class CloudflareAiProvider(
    private val token: String? = System.getenv("CLOUDFLARE_API_TOKEN"),
    private val accountId: String? = System.getenv("CLOUDFLARE_ACCOUNT_ID")
) : BaseHttpProvider() {
    override val name = "cloudflare_ai"
    override fun complete(prompt: String, context: String): String {
        val apiToken = requireKey(token, name)
        val account = requireKey(accountId, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.cloudflare.com/client/v4/accounts/$account/ai/v1/chat/completions",
            model = "@cf/meta/llama-3.1-8b-instruct",
            prompt = prompt,
            context = context,
            bearerToken = apiToken
        )
    }
}

class SambaNovaProvider(
    private val apiKey: String? = System.getenv("SAMBANOVA_API_KEY")
) : BaseHttpProvider() {
    override val name = "sambanova"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.sambanova.ai/v1/chat/completions",
            model = "Meta-Llama-3.3-70B-Instruct",
            prompt = prompt,
            context = context,
            bearerToken = token
        )
    }
}

class DeepSeekDirectProvider(
    private val apiKey: String? = System.getenv("DEEPSEEK_API_KEY")
) : BaseHttpProvider() {
    override val name = "deepseek_direct"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.deepseek.com/chat/completions",
            model = "deepseek-v4-flash",
            prompt = prompt,
            context = context,
            bearerToken = token
        )
    }
}

class OllamaProvider : BaseHttpProvider() {
    override val name = "ollama"

    override fun complete(prompt: String, context: String): String {
        val host = (System.getenv("OLLAMA_HOST") ?: "http://127.0.0.1:11434").trimEnd('/')
        val model = (System.getenv("OLLAMA_MODEL") ?: "llama3.2:1b").trim()
        val predict = (System.getenv("OLLAMA_NUM_PREDICT") ?: "48").toIntOrNull() ?: 48
        val ctx = (System.getenv("OLLAMA_NUM_CTX") ?: "512").toIntOrNull() ?: 512
        val content = jsonEscape(buildPrompt(prompt, context))

        val payload =
            """{"model":"$model","prompt":"$content","stream":false,"options":{"num_predict":$predict,"num_ctx":$ctx}}"""

        val raw = postJson("$host/api/generate", payload)
        return extractOllamaResponse(raw)
    }

    private fun extractOllamaResponse(raw: String): String {
        val match = Regex(""""response"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(raw)
            ?: return raw

        return unescapeJsonString(match.groupValues[1]).trim()
    }
}

private fun buildPrompt(prompt: String, context: String): String {
    return if (context.isBlank()) prompt.trim() else context.trim() + "\n\nTask:\n" + prompt.trim()
}
