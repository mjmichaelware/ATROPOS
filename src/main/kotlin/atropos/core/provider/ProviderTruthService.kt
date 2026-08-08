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
    fun snapshot(
        selectedProvider: String = config.runtime.defaultProvider,
        lastActualProvider: String? = null
    ): ProviderTruthSnapshot {
        val selection = selector.select(selectedProvider)
        val records = registry.getAll().map { descriptor ->
            val missing = missingRequirements(descriptor)
            val keyPresent = descriptor.isLocal || missing.isEmpty()
            val adapterPresent = adapterIntrospection.adapterPresent(descriptor.id)
            val executable = adapterPresent && keyPresent && !descriptor.isPaidLocked() && health(descriptor, keyPresent) != ProviderAvailabilityState.OFFLINE
            ProviderTruthRecord(
                id = descriptor.id,
                category = category(descriptor),
                costMode = descriptor.costMode,
                keyPresent = keyPresent,
                descriptorPresent = true,
                adapterPresent = adapterPresent,
                executableSupport = executable,
                health = health(descriptor, keyPresent),
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
        ProviderTruthOperationRegistry(snapshot())

    private fun missingRequirements(descriptor: ProviderDescriptor): List<String> =
        descriptor.requiredEnv.filterNot(::requirementPresent)

    private fun requirementPresent(name: String): Boolean =
        when (name) {
            "GROQ_API_KEY" -> config.keys.groq.isNotBlank() || envPresent(name)
            "OPENAI_API_KEY" -> config.keys.openai.isNotBlank() || envPresent(name)
            "ANTHROPIC_API_KEY" -> config.keys.anthropic.isNotBlank() || envPresent(name)
            "XAI_API_KEY" -> config.keys.xai.isNotBlank() || envPresent(name)
            "OLLAMA_HOST", "OLLAMA_MODEL" -> true
            else -> envPresent(name)
        }

    private fun envPresent(name: String): Boolean =
        !System.getenv(name).isNullOrBlank()

    private fun health(descriptor: ProviderDescriptor, configured: Boolean): ProviderAvailabilityState =
        when {
            descriptor.id == "ollama" -> if (ollamaProbe()) ProviderAvailabilityState.READY else ProviderAvailabilityState.OFFLINE
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
    private val base: OperationRegistry = StaticOperationRegistry()
) : OperationRegistry {
    private val providerEndpoints: List<OperationEndpoint> = snapshot.records.map { record ->
        OperationEndpoint(
            id = "provider.${record.id}",
            kind = EndpointKind.PROVIDER_CHAT,
            description = "${record.id} descriptor=${record.descriptorPresent} adapter=${record.adapterPresent} executable=${record.executableSupport} health=${record.health.name.lowercase()}",
            configured = record.keyPresent,
            available = record.executableSupport,
            manifest = EndpointManifest(
                owner = "ProviderTruthOperationRegistry",
                input = "typed provider chat request",
                output = "typed provider chat result",
                errors = listOf("authorization", "timeout", "malformed", "unavailable"),
                auth = "policy-bound",
                sideEffects = emptyList(),
                timeoutMs = 30_000,
                retryPolicy = "bounded-none",
                testIds = listOf("OperationEndpointManifestTest.every_registered_operation_exposes_a_complete_manifest")
            )
        ).requireCompleteManifest()
    }

    override fun getAll(): List<OperationEndpoint> =
        base.getAll() + providerEndpoints

    override fun getById(id: String): OperationEndpoint? =
        providerEndpoints.find { it.id == id } ?: base.getById(id)

    override fun getByKind(kind: EndpointKind): List<OperationEndpoint> =
        base.getByKind(kind) + providerEndpoints.filter { it.kind == kind }
}
