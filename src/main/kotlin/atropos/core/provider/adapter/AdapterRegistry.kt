package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderEnvironmentAliases
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
        CANONICAL_KEYS.forEach { canonical ->
            if (resolved[canonical].isNullOrBlank()) {
                val value = ProviderEnvironmentAliases.names(canonical).asSequence()
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
        val CANONICAL_KEYS = ProviderEnvironmentAliases.names("OPENAI_API_KEY").let {
            listOf(
                "OPENAI_API_KEY", "ANTHROPIC_API_KEY", "GROQ_API_KEY", "XAI_API_KEY",
                "GEMINI_API_KEY", "OPENROUTER_API_KEY", "DEEPSEEK_API_KEY", "MISTRAL_API_KEY",
                "FIREWORKS_API_KEY", "TOGETHER_API_KEY", "AZURE_OPENAI_API_KEY"
            )
        }
    }
}
