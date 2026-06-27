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
}

class GroqProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "groq"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        val content = jsonEscape(buildPrompt(prompt, context))
        val payload = """{"model":"llama-3.3-70b-versatile","messages":[{"role":"user","content":"$content"}],"temperature":0.1}"""
        return postJson("https://api.groq.com/openai/v1/chat/completions", payload, bearerToken = token)
    }
}

class OpenAiProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "openai"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        val content = jsonEscape(buildPrompt(prompt, context))
        val payload = """{"model":"gpt-4o-mini","messages":[{"role":"user","content":"$content"}],"temperature":0.1}"""
        return postJson("https://api.openai.com/v1/chat/completions", payload, bearerToken = token)
    }
}

class AnthropicProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "anthropic"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        val content = jsonEscape(buildPrompt(prompt, context))
        val payload = """{"model":"claude-3-5-sonnet-latest","max_tokens":4096,"messages":[{"role":"user","content":"$content"}]}"""
        return postJson(
            "https://api.anthropic.com/v1/messages",
            payload,
            extraHeaders = mapOf(
                "x-api-key" to token,
                "anthropic-version" to "2023-06-01"
            )
        )
    }
}

class XAiProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "xai"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        val content = jsonEscape(buildPrompt(prompt, context))
        val payload = """{"model":"grok-2-latest","messages":[{"role":"user","content":"$content"}],"temperature":0.1}"""
        return postJson("https://api.x.ai/v1/chat/completions", payload, bearerToken = token)
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

    private fun unescapeJsonString(value: String): String {
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

private fun buildPrompt(prompt: String, context: String): String {
    return if (context.isBlank()) prompt.trim() else context.trim() + "\n\nTask:\n" + prompt.trim()
}
