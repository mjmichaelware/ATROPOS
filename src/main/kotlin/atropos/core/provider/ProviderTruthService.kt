package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.OllamaHealthProbe
import atropos.core.agent.AgentProviderSelector
import atropos.core.endpoint.EndpointKind
import atropos.core.endpoint.OperationEndpoint
import atropos.core.endpoint.EndpointManifest
import atropos.core.endpoint.OperationRegistry
import atropos.core.endpoint.StaticOperationRegistry

class ProviderTruthService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val adapterIntrospection: ProviderAdapterIntrospection = ProviderAdapterIntrospection(config),
    private val selector: AgentProviderSelector = AgentProviderSelector(config),
    private val ollamaProbe: () -> Boolean = { OllamaHealthProbe().probe().online }
) {
    private val configuration = ProviderConfigurationResolver(config)

    fun snapshot(
        selectedProvider: String = config.runtime.defaultProvider,
        lastActualProvider: String? = null
    ): ProviderTruthSnapshot {
        val selection = selector.select(selectedProvider)
        val records = registry.getAll().map { descriptor ->
            val missing = configuration.missingRequirements(descriptor)
            val keyPresent = configuration.isConfigured(descriptor)
            val adapterPresent = adapterIntrospection.adapterPresent(descriptor.id)
            val availability = health(descriptor, keyPresent)
            val executable = adapterPresent && keyPresent && !descriptor.isPaidLocked() && availability != ProviderAvailabilityState.OFFLINE
            ProviderTruthRecord(
                id = descriptor.id,
                category = category(descriptor),
                costMode = descriptor.costMode,
                keyPresent = keyPresent,
                descriptorPresent = true,
                adapterPresent = adapterPresent,
                executableSupport = executable,
                health = availability,
                askEligible = descriptor.id in selection.askOrder,
                patchEligible = descriptor.id in selection.patchOrder,
                paidLocked = descriptor.isPaidLocked(),
                missingRequirements = missing
            )
        }
        return ProviderTruthSnapshot(
            selectedProvider = selectedProvider,
            records = records,
            askOrder = selection.askOrder,
            patchOrder = selection.patchOrder,
            lastActualProvider = lastActualProvider,
            paidAutomaticModeLocked = selection.paidAutomaticModeLocked
        )
    }

    fun endpointRegistry(): OperationRegistry =
        ProviderTruthOperationRegistry(snapshot(), descriptorRegistry = registry)

    private fun health(descriptor: ProviderDescriptor, configured: Boolean): ProviderAvailabilityState =
        when {
            descriptor.isLocal && descriptor.hasCapability(ApiCapability.CHAT) ->
                if (ollamaProbe()) ProviderAvailabilityState.READY else ProviderAvailabilityState.OFFLINE
            descriptor.isPaidLocked() -> ProviderAvailabilityState.DISABLED
            !configured -> ProviderAvailabilityState.AUTH_FAILED
            adapterIntrospection.adapterPresent(descriptor.id) -> ProviderAvailabilityState.READY
            else -> ProviderAvailabilityState.UNKNOWN
        }

    private fun category(descriptor: ProviderDescriptor): String =
        when {
            descriptor.isLocal -> "local"
            descriptor.hasCapability(ApiCapability.CHAT) || descriptor.hasCapability(ApiCapability.CODE) -> "llm"
            descriptor.hasCapability(ApiCapability.ASSET) || descriptor.hasCapability(ApiCapability.VISION) -> "asset"
            descriptor.hasCapability(ApiCapability.STORAGE) || descriptor.hasCapability(ApiCapability.DATABASE) -> "storage"
            descriptor.hasCapability(ApiCapability.WEB) || descriptor.hasCapability(ApiCapability.READER) -> "web"
            descriptor.hasCapability(ApiCapability.CI) -> "ci"
            else -> "service"
        }
}

class ProviderTruthOperationRegistry(
    private val snapshot: ProviderTruthSnapshot,
    private val base: OperationRegistry = StaticOperationRegistry(),
    private val descriptorRegistry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry()
) : OperationRegistry {
    private val providerEndpoints: List<OperationEndpoint> = snapshot.records.map { record ->
        val descriptor = descriptorRegistry.getById(record.id)
        val endpointId = descriptor?.endpointId?.takeIf { it.isNotBlank() } ?: "provider.${record.id}"
        OperationEndpoint(
            id = endpointId,
            kind = endpointKind(endpointId),
            description = "${descriptor?.displayName ?: record.id} descriptor=${record.descriptorPresent} adapter=${record.adapterPresent} executable=${record.executableSupport} health=${record.health.name.lowercase()}",
            configured = record.keyPresent,
            available = record.executableSupport,
            manifest = EndpointManifest(
                owner = "ProviderTruthOperationRegistry",
                input = "typed provider chat request",
                output = "typed provider chat result",
                errors = listOf("authorization", "timeout", "malformed", "unavailable"),
                auth = "policy-bound",
                sideEffects = listOf("none"),
                timeoutMs = 30_000,
                retryPolicy = "bounded-none",
                testIds = listOf("OperationEndpointManifestTest.every_registered_operation_exposes_a_complete_manifest")
            )
        ).requireCompleteManifest()
    }

    init {
        require(providerEndpoints.map { it.id }.distinct().size == providerEndpoints.size) {
            "provider truth endpoint registry contains duplicate endpoint ids"
        }
    }

    override fun getAll(): List<OperationEndpoint> =
        base.getAll() + providerEndpoints

    override fun getById(id: String): OperationEndpoint? =
        providerEndpoints.find { it.id == id } ?: base.getById(id)

    override fun getByKind(kind: EndpointKind): List<OperationEndpoint> =
        base.getByKind(kind) + providerEndpoints.filter { it.kind == kind }

    private fun endpointKind(endpointId: String): EndpointKind = when {
        endpointId.endsWith(".messages") -> EndpointKind.PROVIDER_MESSAGES
        endpointId.endsWith(".generate") -> EndpointKind.PROVIDER_GENERATE
        endpointId.endsWith(".tags") -> EndpointKind.PROVIDER_TAGS
        else -> EndpointKind.PROVIDER_CHAT
    }
}
