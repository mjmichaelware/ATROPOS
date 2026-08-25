package atropos.core.provider

/** One credential-name contract shared by discovery, activation, and adapters. */
object ProviderEnvironmentAliases {
    private val aliases = mapOf(
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
        "AZURE_OPENAI_API_KEY" to listOf("AZURE_API_KEY")
    )

    fun names(canonical: String): List<String> =
        (listOf(canonical) + aliases[canonical].orEmpty() +
            listOf(
                "ATROPOS_PROVIDER_$canonical",
                "ATROPOS_PROVIDER_${canonical.substringBefore("_API_KEY")}_API_KEY"
            )).distinct()
}
