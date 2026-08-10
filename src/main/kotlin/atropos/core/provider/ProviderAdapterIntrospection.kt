package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.provider.adapter.ProviderAdapterRegistry
import atropos.core.provider.adapter.StaticProviderAdapterRegistry

class ProviderAdapterIntrospection(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val adapterRegistry: ProviderAdapterRegistry = StaticProviderAdapterRegistry()
) {
    fun adapterPresent(providerId: String): Boolean =
        adapterRegistry.getByProviderId(providerId)?.status()?.implemented == true
}
