package atropos.core

class GroqProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "groq"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        return postOpenAiCompatibleChat(
            uri = "https://api.groq.com/openai/v1/chat/completions",
            model = "llama-3.3-70b-versatile",
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
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
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
            bearerToken = token
        )
    }
}

class AnthropicProvider(private val apiKey: String?) : BaseHttpProvider() {
    override val name = "anthropic"
    override fun complete(prompt: String, context: String): String {
        val token = requireKey(apiKey, name)
        val redactedPrompt = redactionFilter.redact(prompt)
        val redactedContext = redactionFilter.redact(context)
        val payload = buildString {
            append("{")
            append("\"model\":\"claude-3-5-sonnet-latest\",")
            append("\"max_tokens\":4096,")
            if (redactedContext.isNotBlank()) {
                append("\"system\":\"").append(jsonEscape(redactedContext.trim())).append("\",")
            }
            append("\"messages\":[{\"role\":\"user\",\"content\":\"")
            append(jsonEscape(redactedPrompt.trim()))
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
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
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
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
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
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
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
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
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
            prompt = redactionFilter.redact(prompt),
            context = redactionFilter.redact(context),
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
        val redactedPrompt = redactionFilter.redact(prompt)
        val redactedContext = redactionFilter.redact(context)
        val content = jsonEscape(buildPrompt(redactedPrompt, redactedContext))

        val payload =
            """{"model":"$model","prompt":"$content","stream":false,"options":{"num_predict":$predict,"num_ctx":$ctx}}"""

        val raw = postJson("$host/api/generate", payload)
        return extractOllamaResponse(raw)
    }

    private fun extractOllamaResponse(raw: String): String {
        // A model response is long by construction, which is exactly the input
        // the regex here used to overflow the stack on.
        val field = atropos.core.json.JsonStringField.value(raw, "response") ?: return raw

        return unescapeJsonString(field).trim()
    }
}

private fun buildPrompt(prompt: String, context: String): String {
    return if (context.isBlank()) prompt.trim() else context.trim() + "\n\nTask:\n" + prompt.trim()
}
