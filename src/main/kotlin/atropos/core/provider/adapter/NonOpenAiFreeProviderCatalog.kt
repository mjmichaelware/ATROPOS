package atropos.core.provider.adapter

object NonOpenAiFreeProviderCatalog {
    private val specs = listOf(
        NonOpenAiProviderSpec(
            providerId = "gemini",
            displayName = "Google Gemini",
            schema = NonOpenAiProviderSchema.GEMINI,
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            defaultModel = "gemini-1.5-flash",
            fallbackModels = listOf("gemini-1.5-flash-8b"),
            requiredEnv = listOf("GEMINI_API_KEY")
        ),
        NonOpenAiProviderSpec(
            providerId = "github_models",
            displayName = "GitHub Models",
            schema = NonOpenAiProviderSchema.GITHUB_MODELS,
            endpoint = "https://models.inference.ai.azure.com/chat/completions",
            defaultModel = "gpt-4o-mini",
            fallbackModels = listOf("Phi-3.5-mini-instruct"),
            requiredEnv = listOf("GITHUB_MODELS_TOKEN")
        ),
        NonOpenAiProviderSpec(
            providerId = "cloudflare_ai",
            displayName = "Cloudflare AI",
            schema = NonOpenAiProviderSchema.CLOUDFLARE_AI,
            endpoint = "https://api.cloudflare.com/client/v4/accounts/{account}/ai/run/{model}",
            defaultModel = "@cf/meta/llama-3.1-8b-instruct",
            fallbackModels = listOf("@cf/mistral/mistral-7b-instruct-v0.1"),
            requiredEnv = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID")
        ),
        NonOpenAiProviderSpec(
            providerId = "cloudflare_workers",
            displayName = "Cloudflare Workers",
            schema = NonOpenAiProviderSchema.CLOUDFLARE_WORKERS,
            endpoint = "cloudflare-workers-deployment-manifest",
            defaultModel = "worker-module",
            fallbackModels = listOf("pages-function"),
            requiredEnv = listOf("CLOUDFLARE_API_TOKEN", "CLOUDFLARE_ACCOUNT_ID")
        )
    )

    fun get(providerId: String): NonOpenAiProviderSpec? =
        specs.firstOrNull { it.providerId == providerId }

    fun all(): List<NonOpenAiProviderSpec> = specs
}
