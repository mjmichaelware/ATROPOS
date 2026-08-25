package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.security.DefaultSecretSource
import atropos.core.security.SecretSource

interface ProviderAdapterRegistry {
    fun getAll(): List<ProviderAdapter>
    fun getByProviderId(providerId: String): ProviderAdapter?
    fun getByCapability(capability: ApiCapability): List<ProviderAdapter>
    fun status(): List<AdapterStatus>
}

class StaticProviderAdapterRegistry(
    descriptorRegistry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val env: Map<String, String> = System.getenv(),
    private val secretSource: SecretSource = DefaultSecretSource.create(env = env)
) : ProviderAdapterRegistry {
    private val effectiveEnv: Map<String, String> = resolveAliases(env)
    private val adapters: List<ProviderAdapter> =
        descriptorRegistry.getAll().map { buildKernelAdapter(it, effectiveEnv) }

    override fun getAll(): List<ProviderAdapter> =
        adapters

    override fun getByProviderId(providerId: String): ProviderAdapter? =
        adapters.firstOrNull { it.providerId == providerId }

    override fun getByCapability(capability: ApiCapability): List<ProviderAdapter> =
        adapters.filter { capability in it.capabilities }

    override fun status(): List<AdapterStatus> =
        adapters.map { it.status() }

    private fun resolveAliases(source: Map<String, String>): Map<String, String> {
        val resolved = source.toMutableMap()
        ALIASES.forEach { (canonical, aliases) ->
            if (resolved[canonical].isNullOrBlank()) {
                val namespaceAliases = listOf(
                    "ATROPOS_PROVIDER_$canonical",
                    "ATROPOS_PROVIDER_${canonical.substringBefore("_API_KEY")}_API_KEY"
                ).distinct()
                val value = (listOf(canonical) + aliases + namespaceAliases).asSequence()
                    .mapNotNull { name ->
                        resolved[name]?.takeIf(String::isNotBlank)
                            ?: secretSource.lookup(name).value?.takeIf(String::isNotBlank)
                    }
                    .firstOrNull()
                if (value != null) resolved[canonical] = value
            }
        }
        return resolved
    }

    private companion object {
        val ALIASES = mapOf(
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
    }
}
