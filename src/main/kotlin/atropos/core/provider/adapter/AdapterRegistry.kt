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
    private val adapters: List<ProviderAdapter> =
        descriptorRegistry.getAll().map(::buildKernelAdapter)

    override fun getAll(): List<ProviderAdapter> =
        adapters

    override fun getByProviderId(providerId: String): ProviderAdapter? =
        adapters.firstOrNull { it.providerId == providerId }

    override fun getByCapability(capability: ApiCapability): List<ProviderAdapter> =
        adapters.filter { capability in it.capabilities }

    override fun status(): List<AdapterStatus> =
        adapters.map { it.status() }
}
