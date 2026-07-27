package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.ProviderFactory

class ProviderAdapterIntrospection(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val factory: ProviderFactory = ProviderFactory(config)
) {
    fun adapterPresent(providerId: String): Boolean =
        runCatching { factory.getProvider(providerId) }.isSuccess
}
