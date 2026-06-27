package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry

interface ProviderAdapterRegistry {
    fun getAll(): List<ProviderAdapter>
    fun getByProviderId(providerId: String): ProviderAdapter?
    fun getByCapability(capability: ApiCapability): List<ProviderAdapter>
    fun status(): List<AdapterStatus>
}

class StaticProviderAdapterRegistry(
    descriptorRegistry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
) : ProviderAdapterRegistry {
    private val adapters: List<ProviderAdapter> = buildList {
        add(LocalMockAdapter(descriptorRegistry))
        add(OllamaScaffoldAdapter(descriptorRegistry))

        add(GroqScaffoldAdapter(descriptorRegistry))
        add(GeminiScaffoldAdapter(descriptorRegistry))
        add(OpenRouterScaffoldAdapter(descriptorRegistry))
        add(GitHubModelsScaffoldAdapter(descriptorRegistry))
        add(CloudflareAiScaffoldAdapter(descriptorRegistry))

        add(HuggingFaceScaffoldAdapter(descriptorRegistry))
        add(NvidiaNimScaffoldAdapter(descriptorRegistry))
        add(DeepInfraScaffoldAdapter(descriptorRegistry))
        add(SiliconFlowScaffoldAdapter(descriptorRegistry))
        add(CerebrasScaffoldAdapter(descriptorRegistry))
        add(SambaNovaScaffoldAdapter(descriptorRegistry))

        add(JinaReaderScaffoldAdapter(descriptorRegistry))
        add(SerpApiScaffoldAdapter(descriptorRegistry))
        add(GoogleDriveScaffoldAdapter(descriptorRegistry))
        add(LocalScraperScaffoldAdapter(descriptorRegistry))
    }

    override fun getAll(): List<ProviderAdapter> = adapters

    override fun getByProviderId(providerId: String): ProviderAdapter? =
        adapters.find { it.providerId == providerId }

    override fun getByCapability(capability: ApiCapability): List<ProviderAdapter> =
        adapters.filter { capability in it.capabilities }

    override fun status(): List<AdapterStatus> =
        adapters.map { it.status() }
}
