package atropos.core.provider

/** One credential-name contract shared by discovery, activation, and adapters. */
object ProviderEnvironmentAliases {
    private val aliases = mapOf(
        // The base URL is routing metadata, never a credential alias. Keeping
        // it out of this list prevents endpoint-only environments from being
        // classified as configured or entering the provider cascade.
        "OPENAI_API_KEY" to listOf("OPENAI_KEY", "OPENAI_TOKEN"),
        "ANTHROPIC_API_KEY" to listOf("ANTHROPIC_KEY", "CLAUDE_API_KEY", "CLAUDE_TOKEN"),
        "GROQ_API_KEY" to listOf("GROQ_KEY", "GROQ_TOKEN"),
        "XAI_API_KEY" to listOf("XAI_KEY", "GROK_API_KEY", "GROK_TOKEN"),
        "GEMINI_API_KEY" to listOf("GOOGLE_API_KEY", "GOOGLE_GEMINI_API_KEY"),
        "OPENROUTER_API_KEY" to listOf("OPENROUTER_KEY"),
        "DEEPSEEK_API_KEY" to listOf("DEEPSEEK_KEY"),
        "MISTRAL_API_KEY" to listOf("MISTRAL_TOKEN"),
        "FIREWORKS_API_KEY" to listOf("FIREWORKS_AI_API_KEY"),
        "TOGETHER_API_KEY" to listOf("TOGETHERAI_API_KEY"),
        "AZURE_OPENAI_API_KEY" to listOf("AZURE_API_KEY"),
        "AWS_REGION" to listOf("AWS_DEFAULT_REGION", "AWS_PROFILE"),
        "OLLAMA_HOST" to listOf("OLLAMA_MODEL")
    )

    private val providerCanonical = mapOf(
        "openai" to "OPENAI_API_KEY",
        "anthropic" to "ANTHROPIC_API_KEY",
        "groq" to "GROQ_API_KEY",
        "xai" to "XAI_API_KEY",
        "gemini" to "GEMINI_API_KEY",
        "openrouter" to "OPENROUTER_API_KEY",
        "together" to "TOGETHER_API_KEY",
        "deepseek_direct" to "DEEPSEEK_API_KEY",
        "mistral" to "MISTRAL_API_KEY",
        "fireworks" to "FIREWORKS_API_KEY",
        "azure_openai" to "AZURE_OPENAI_API_KEY",
        "aws_bedrock" to "AWS_ACCESS_KEY_ID",
        "ollama" to "OLLAMA_HOST"
    )

    fun names(canonical: String): List<String> =
        (listOf(canonical) + aliases[canonical].orEmpty() +
            aliases[canonical].orEmpty().map { "ATROPOS_PROVIDER_$it" } +
            listOf(
                "ATROPOS_PROVIDER_$canonical",
                "ATROPOS_PROVIDER_${canonical.substringBefore("_API_KEY")}_API_KEY"
            )).distinct()

    fun forProvider(providerId: String, requiredEnv: List<String> = emptyList()): List<String> =
        (providerCanonical[providerId]?.let(::names).orEmpty() +
            requiredEnv.flatMap(::names) + requiredEnv).distinct()
}
