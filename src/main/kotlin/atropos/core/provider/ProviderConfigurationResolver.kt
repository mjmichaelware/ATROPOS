package atropos.core.provider

import atropos.core.AtroposConfig

/** Resolves provider requirements from the canonical descriptor metadata. */
class ProviderConfigurationResolver(
    private val config: AtroposConfig = AtroposConfig.load()
) {
    fun missingRequirements(descriptor: ProviderDescriptor): List<String> =
        descriptor.requiredEnv.filterNot(::isPresent)

    fun isConfigured(descriptor: ProviderDescriptor): Boolean =
        descriptor.isLocal || missingRequirements(descriptor).isEmpty()

    private fun isPresent(name: String): Boolean = when (name) {
        "GROQ_API_KEY" -> config.keys.groq.isNotBlank() || environmentPresent(name)
        "OPENAI_API_KEY" -> config.keys.openai.isNotBlank() || environmentPresent(name)
        "ANTHROPIC_API_KEY" -> config.keys.anthropic.isNotBlank() || environmentPresent(name)
        "XAI_API_KEY" -> config.keys.xai.isNotBlank() || environmentPresent(name)
        "OLLAMA_HOST", "OLLAMA_MODEL" -> true
        else -> environmentPresent(name)
    }

    private fun environmentPresent(name: String): Boolean =
        !System.getenv(name).isNullOrBlank()
}
