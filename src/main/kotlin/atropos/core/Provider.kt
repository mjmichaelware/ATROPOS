package atropos.core
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

interface AIProvider {
    val name: String
    fun complete(prompt: String, context: String): String
}

class ProviderFactory(private val config: AtroposConfig) {
    fun getProvider(name: String): AIProvider {
        return when (name.lowercase()) {
            "groq" -> GroqProvider(config.keys.groq)
            "openai" -> OpenAiProvider(config.keys.openai)
            "anthropic" -> AnthropicProvider(config.keys.anthropic)
            "xai" -> XAiProvider(config.keys.xai)
            else -> GroqProvider(config.keys.groq)
        }
    }
}

abstract class BaseHttpProvider : AIProvider {
    protected val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    protected fun clean(raw: String) = raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    protected fun parse(json: String, marker: String): String {
        val idx = json.indexOf(marker)
        if (idx == -1) return "Response body raw footprint frame output: $json"
        val start = json.indexOf("\"", idx + marker.length) + 1
        val end = json.indexOf("\"", start)
        if (start <= 0 || end == -1) return "Data chunk boundary tracking error."
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"")
    }
}

class GroqProvider(private val apiKey: String) : BaseHttpProvider() {
    override val name = "Groq"
    override fun complete(prompt: String, context: String): String {
        if (apiKey.isEmpty()) return "Error: Groq credential bindings are absent."
        val payload = "{\"model\":\"llama-3.3-70b-versatile\",\"messages\":[{\"role\":\"user\",\"content\":\"${clean(prompt)}\"}],\"temperature\":0.2}"
        val request = HttpRequest.newBuilder().uri(URI.create("https://api.groq.com/openai/v1/chat/completions")).header("Content-Type", "application/json").header("Authorization", "Bearer $apiKey").POST(HttpRequest.BodyPublishers.ofString(payload)).build()
        return try { val res = client.send(request, HttpResponse.BodyHandlers.ofString()); if (res.statusCode() != 200) "HTTP Error ${res.statusCode()}" else parse(res.body(), "\"content\"") } catch (e: Exception) { "Network fault: ${e.message}" }
    }
}

class OpenAiProvider(private val apiKey: String) : BaseHttpProvider() {
    override val name = "OpenAI"
    override fun complete(prompt: String, context: String): String { return "OpenAI placeholder channel response pass successfully mapped." }
}
class AnthropicProvider(private val apiKey: String) : BaseHttpProvider() {
    override val name = "Anthropic"
    override fun complete(prompt: String, context: String): String { return "Anthropic placeholder channel response pass successfully mapped." }
}
class XAiProvider(private val apiKey: String) : BaseHttpProvider() {
    override val name = "xAI"
    override fun complete(prompt: String, context: String): String { return "xAI placeholder channel response pass successfully mapped." }
}
