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
    val paidAutomaticModeLocked: Boolean = true,
    val localFallbackEnabled: Boolean = true
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
            ?.takeIf { !it.isPaidLocked() && (it.hasCapability(ApiCapability.CODE) || it.hasCapability(ApiCapability.REPAIR)) }
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

        // Local-first, then free-first. Descriptor order provides the stable
        // peer preference; cost ordering outranks it and paid providers are
        // removed before any attempt.
        val localFallback = registry.getAll().firstOrNull {
            it.isLocal && it.hasCapability(ApiCapability.CHAT)
        }?.id ?: activeCandidate.takeIf { it.isNotBlank() }
        val orderedAsk = ProviderCascadeOrder.order(finalOrder).ifEmpty {
            localFallback?.let { listOf(it) } ?: emptyList()
        }
        val orderedPatch = if (requestedPatchDescriptor != null) {
            // An explicit override stays first: the operator named it.
            listOf(requestedPatchDescriptor.id) +
                ProviderCascadeOrder.order(patchOrder.filterNot { it == requestedPatchDescriptor.id })
        } else {
            ProviderCascadeOrder.order(patchOrder)
        }

        return AgentProviderSelection(
            askOrder = orderedAsk,
            patchOrder = orderedPatch,
            doctorTruthSource = doctorTruthSource,
            knownActiveProviders = knownActive
        )
    }

    private fun candidatesFor(vararg capabilities: ApiCapability): List<String> = registry.getAll()
        .filter { descriptor ->
            capabilities.any(descriptor::hasCapability) &&
                configuration.isConfigured(descriptor) &&
                adapterIntrospection.adapterPresent(descriptor.id) &&
                (!requiresHealthProbe(descriptor) || ollamaProbe())
        }
        .map(ProviderDescriptor::id)

    private fun requiresHealthProbe(descriptor: ProviderDescriptor): Boolean =
        descriptor.isLocal && descriptor.hasCapability(ApiCapability.CHAT)
}
