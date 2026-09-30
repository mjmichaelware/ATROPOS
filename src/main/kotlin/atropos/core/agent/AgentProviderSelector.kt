package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.core.OllamaHealthProbe
import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderCascadeOrder
import atropos.core.provider.ProviderAdapterIntrospection
import atropos.core.provider.ProviderConfigurationResolver
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.StaticProviderDescriptorRegistry

data class AgentProviderSelection(
    val askOrder: List<String>,
    val patchOrder: List<String>,
    val doctorTruthSource: String,
    val knownActiveProviders: List<String>,
    val paidAutomaticModeLocked: Boolean = true
)

class AgentProviderSelector(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val ollamaProbe: () -> Boolean = { OllamaHealthProbe().probe().online },
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val adapterIntrospection: ProviderAdapterIntrospection = ProviderAdapterIntrospection(config),
    private val configuration: ProviderConfigurationResolver = ProviderConfigurationResolver(config)
) {
    private val doctorTruthSource = "canonical provider descriptor registry"

    fun select(
        activeProviderName: String = config.runtime.defaultProvider,
        patchProviderOverride: String? = null
    ): AgentProviderSelection {
        val finalOrder = candidatesFor(ApiCapability.CHAT)
        val patchConfigured = candidatesFor(ApiCapability.CODE, ApiCapability.REPAIR)

        val requestedPatchProvider = patchProviderOverride?.trim()?.lowercase().orEmpty()
        val requestedPatchDescriptor = registry.getById(requestedPatchProvider)
            ?.takeIf { !it.isPaid() && (it.hasCapability(ApiCapability.CODE) || it.hasCapability(ApiCapability.REPAIR)) }
        val patchOrder = buildList {
            if (requestedPatchDescriptor != null) {
                add(requestedPatchDescriptor.id)
            }
            patchConfigured.forEach { provider ->
                if (provider != requestedPatchDescriptor?.id) add(provider)
            }
        }.ifEmpty {
            if (requestedPatchDescriptor != null) listOf(requestedPatchDescriptor.id) else emptyList()
        }

        val activeCandidate = activeProviderName.trim().lowercase()
        val knownActive = (finalOrder + patchConfigured + activeCandidate)
            .filter { it.isNotBlank() }
            .distinct()

        // Free-first, cost-ordered. Descriptor order provides the stable
        // peer preference; cost ordering outranks it and paid providers are
        // removed before any attempt.
        val orderedAsk = ProviderCascadeOrder.order(finalOrder)
        val orderedPatch = ProviderCascadeOrder.order(
            candidatesFor(ApiCapability.CODE, ApiCapability.REPAIR)
        )

        return AgentProviderSelection(
            askOrder = orderedAsk,
            patchOrder = orderedPatch,
            doctorTruthSource = "canonical provider descriptor registry",
            knownActiveProviders = finalOrder
        )
    }

    private fun candidatesFor(vararg capabilities: ApiCapability): List<String> = registry.getAll()
        .filter { descriptor ->
            descriptor.hasCapability(ApiCapability.CHAT) &&
                (capabilities.isEmpty() || capabilities.any(descriptor::hasCapability)) &&
                // All providers should be configurable
                true
        }
        .map(ProviderDescriptor::id)
}
