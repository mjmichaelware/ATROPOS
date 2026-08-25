package atropos.core.provider

import atropos.core.AtroposConfig

/** Resolves provider requirements from the canonical descriptor metadata. */
class ProviderConfigurationResolver(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val environment: Map<String, String> = System.getenv()
) {
    fun missingRequirements(descriptor: ProviderDescriptor): List<String> =
        descriptor.requiredEnv.filterNot(::isPresent)

    fun isConfigured(descriptor: ProviderDescriptor): Boolean =
        descriptor.isLocal || missingRequirements(descriptor).isEmpty()

    private fun isPresent(name: String): Boolean {
        if (name == "OLLAMA_HOST" || name == "OLLAMA_MODEL") return true
        val configPresent = when (name) {
            "GROQ_API_KEY" -> config.keys.groq.isNotBlank()
            "OPENAI_API_KEY" -> config.keys.openai.isNotBlank()
            "ANTHROPIC_API_KEY" -> config.keys.anthropic.isNotBlank()
            "XAI_API_KEY" -> config.keys.xai.isNotBlank()
            else -> false
        }
        return configPresent || ProviderEnvironmentAliases.names(name).any(::environmentPresent)
    }

    private fun environmentPresent(name: String): Boolean =
        !environment[name].isNullOrBlank()
}
