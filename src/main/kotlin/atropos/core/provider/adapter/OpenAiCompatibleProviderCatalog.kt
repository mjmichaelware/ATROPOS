package atropos.core.provider.adapter

object OpenAiCompatibleProviderCatalog {
    private val specs = listOf(
        OpenAiCompatibleProviderSpec(
            providerId = "groq",
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/chat/completions",
            defaultModel = "llama-3.1-8b-instant",
            fallbackModels = listOf("llama-3.3-70b-versatile"),
            apiKeyEnv = "GROQ_API_KEY",
            freeTier = true
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "openrouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "meta-llama/llama-3.1-8b-instruct:free",
            fallbackModels = listOf("mistralai/mistral-7b-instruct:free"),
            apiKeyEnv = "OPENROUTER_API_KEY",
            freeTier = true,
            headers = mapOf(
                "HTTP-Referer" to "https://local.atropos.invalid",
                "X-Title" to "ATROPOS"
            )
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "deepinfra",
            displayName = "DeepInfra",
            baseUrl = "https://api.deepinfra.com/v1/openai/chat/completions",
            defaultModel = "meta-llama/Meta-Llama-3.1-8B-Instruct",
            fallbackModels = listOf("mistralai/Mistral-7B-Instruct-v0.3"),
            apiKeyEnv = "DEEPINFRA_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "siliconflow",
            displayName = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1/chat/completions",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            fallbackModels = listOf("THUDM/glm-4-9b-chat"),
            apiKeyEnv = "SILICONFLOW_API_KEY",
            freeTier = false
        )
    )

    fun get(providerId: String): OpenAiCompatibleProviderSpec? =
        specs.firstOrNull { it.providerId == providerId }

    fun all(): List<OpenAiCompatibleProviderSpec> = specs
}
