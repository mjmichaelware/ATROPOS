package atropos.core

import atropos.core.security.RedactionFilter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ProviderHttpClient(private val redactionFilter: RedactionFilter = RedactionFilter()) {
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(45)).build()

    fun postJson(uri: String, payload: String, bearerToken: String? = null, extraHeaders: Map<String, String> = emptyMap()): String {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds((System.getenv("ATROPOS_HTTP_TIMEOUT_SECONDS") ?: "240").toLongOrNull() ?: 240L))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
        if (!bearerToken.isNullOrBlank()) builder.header("Authorization", "Bearer $bearerToken")
        for ((k, v) in extraHeaders) builder.header(k, v)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw RuntimeException("HTTP ${response.statusCode()} :: ${response.body()}")
        return response.body()
    }

    fun getJson(uri: String, bearerToken: String? = null, extraHeaders: Map<String, String> = emptyMap()): String {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .timeout(Duration.ofSeconds((System.getenv("ATROPOS_HTTP_TIMEOUT_SECONDS") ?: "120").toLongOrNull() ?: 120L))
            .GET()
        if (!bearerToken.isNullOrBlank()) builder.header("Authorization", "Bearer $bearerToken")
        for ((k, v) in extraHeaders) builder.header(k, v)
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) throw RuntimeException("HTTP ${response.statusCode()}")
        return response.body()
    }

    fun postOpenAiCompatibleChat(uri: String, model: String, prompt: String, context: String = "", bearerToken: String? = null, extraHeaders: Map<String, String> = emptyMap(), temperature: Double = 0.1): String {
        val payload = buildOpenAiCompatiblePayload(model, prompt, context, temperature)
        val raw = postJson(uri, payload, bearerToken = bearerToken, extraHeaders = extraHeaders)
        return extractOpenAiChatContent(raw) ?: raw.trim()
    }

    fun buildOpenAiCompatiblePayload(model: String, prompt: String, context: String = "", temperature: Double = 0.1): String = buildString {
        append("""{"model":"$model","messages":[""")
        if (context.isNotBlank()) append("""{"role":"system","content":"${jsonEscape(context)}"},""")
        append("""{"role":"user","content":"${jsonEscape(prompt)}"}],"temperature":$temperature}""")
    }

    fun extractOpenAiChatContent(json: String): String? {
        val start = json.indexOf(""""content":""")
        if (start < 0) return null
        val contentStart = json.indexOf('"', start + 11) + 1
        val contentEnd = json.indexOf('"', contentStart)
        return if (contentEnd > contentStart) json.substring(contentStart, contentEnd) else null
    }

    fun jsonEscape(input: String): String {
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

    fun requireKey(token: String?, providerName: String): String {
        if (token.isNullOrBlank()) throw IllegalStateException("$providerName API key is missing.")
        return token
    }
}
