package atropos.core.provider

import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.OllamaHealthProbe
import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.adapter.AdapterRequest
import atropos.core.provider.adapter.ProviderAdapter
import atropos.core.provider.adapter.ProviderAdapterRegistry
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ProviderActionProposals
import atropos.core.security.DefaultSecretSource
import atropos.core.security.SecretLookup
import atropos.core.security.SecretSource
import atropos.core.security.RedactionFilter
import java.io.File

class ProviderActivationService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val adapterRegistry: ProviderAdapterRegistry = StaticProviderAdapterRegistry(registry),
    private val secretSource: SecretSource = DefaultSecretSource.create(),
    private val quotaLedger: QuotaLedger = FileQuotaLedger(
        AtroposRepoRootLocator.resolve().resolve(".atropos/provider/quota-ledger.tsv").toFile(),
        FileQuotaLedger.seedFromDescriptors(registry)
    ),
    private val fixtureMatrix: ProviderFixtureMatrixService = ProviderFixtureMatrixService(registry, adapterRegistry),
    private val store: ProviderActivationStore = ProviderActivationStore(),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val ollamaProbe: () -> Boolean = { OllamaHealthProbe().probe().online },
    private val environment: Map<String, String> = System.getenv()
) {
    private val agencyGate = BoundedAgencyGate()

    fun snapshot(providerId: String): ProviderActivationRecord =
        createRecord(providerId, ProviderVerificationMode.SNAPSHOT, live = false, persist = false)

    fun verify(providerId: String): ProviderActivationRecord =
        createRecord(providerId, ProviderVerificationMode.VERIFY, live = false, persist = true)

    fun verifyAll(): List<ProviderActivationRecord> =
        registry.getAll().map { verify(it.id) }

    fun liveTest(providerId: String): ProviderActivationRecord =
        createRecord(providerId, ProviderVerificationMode.LIVE_TEST, live = true, persist = true)

    fun renderVerifyAll(): String = RedactionFilter().redact(buildString {
        appendLine("providers verify:")
        verifyAll().forEach { record ->
            appendLine(
                "  ${record.providerId.padEnd(18)} state=${record.state.name.lowercase()} " +
                    "fixtures=${record.fixtureMatrix?.summary() ?: "0/0"} remediation=${record.remediation}"
            )
        }
    }.trimEnd())

    private fun createRecord(
        providerId: String,
        mode: ProviderVerificationMode,
        live: Boolean,
        persist: Boolean
    ): ProviderActivationRecord {
        val descriptor = registry.getById(providerId)
        if (descriptor == null) {
            return ProviderActivationRecord(
                providerId = providerId,
                mode = mode,
                state = ProviderActivationState.MISSING,
                descriptorPresent = false,
                adapterStatus = null,
                keySources = emptyList(),
                impact = emptyList(),
                executableSupport = false,
                fixtureMatrix = null,
                verificationSummary = "provider descriptor missing",
                remediation = "register provider descriptor"
            ).also { if (persist) store.write(it) }
        }

        val adapter = adapterRegistry.getByProviderId(providerId)
        val adapterStatus = adapter?.status()
        val keyLookups = descriptor.requiredEnv.map(secretSource::lookup)
        val fixture = fixtureMatrix.runProvider(providerId)
        val executableSupport = adapterStatus?.implemented == true && !adapterStatus.dryRunOnly
        val impact = descriptor.capabilities.map { it.name.lowercase() }.sorted()
        val record = if (live) {
            liveRecord(descriptor, adapter, adapterStatus, keyLookups, fixture, impact, executableSupport, mode)
        } else {
            val configuredForExecution = descriptor.isLocal || keyLookups.all { it.configured }
            val storedRecord = store.read(providerId)
            val state = when {
                mode == ProviderVerificationMode.VERIFY && descriptor.isPaidLocked() && !paidGate.isProviderUnlocked(providerId) -> ProviderActivationState.LOCKED
                mode == ProviderVerificationMode.VERIFY && executableSupport && fixture.passed && configuredForExecution -> ProviderActivationState.VERIFIED
                storedRecord != null && (storedRecord.state == ProviderActivationState.VERIFIED || storedRecord.state == ProviderActivationState.READY) -> storedRecord.state
                else -> snapshotState(descriptor, adapterStatus, keyLookups, fixture)
            }
            ProviderActivationRecord(
                providerId = providerId,
                mode = mode,
                state = state,
                descriptorPresent = true,
                adapterStatus = adapterStatus,
                keySources = keyLookups.map { "${it.name}:${it.source}" },
                impact = impact,
                executableSupport = executableSupport,
                fixtureMatrix = fixture,
                verificationSummary = offlineSummary(adapterStatus, fixture),
                remediation = remediation(state, descriptor, adapterStatus, keyLookups)
            )
        }

        if (persist) store.write(record)
        return record
    }

    private fun liveRecord(
        descriptor: ProviderDescriptor,
        adapter: ProviderAdapter?,
        adapterStatus: atropos.core.provider.adapter.AdapterStatus?,
        keyLookups: List<SecretLookup>,
        fixture: ProviderFixtureMatrixRecord,
        impact: List<String>,
        executableSupport: Boolean,
        mode: ProviderVerificationMode
    ): ProviderActivationRecord {
        if (descriptor.isPaidLocked() && !paidGate.isProviderUnlocked(descriptor.id)) {
            return ProviderActivationRecord(
                providerId = descriptor.id,
                mode = mode,
                state = ProviderActivationState.LOCKED,
                descriptorPresent = true,
                adapterStatus = adapterStatus,
                keySources = keyLookups.map { "${it.name}:${it.source}" },
                impact = impact,
                executableSupport = executableSupport,
                fixtureMatrix = fixture,
                verificationSummary = "paid provider live test refused",
                remediation = "keep paid providers locked or use /paid unlock explicitly"
            )
        }
        if (adapter == null) {
            return ProviderActivationRecord(
                providerId = descriptor.id,
                mode = mode,
                state = ProviderActivationState.MISSING,
                descriptorPresent = true,
                adapterStatus = null,
                keySources = keyLookups.map { "${it.name}:${it.source}" },
                impact = impact,
                executableSupport = false,
                fixtureMatrix = fixture,
                verificationSummary = "provider adapter missing",
                remediation = "implement provider adapter"
            )
        }

        val result = completeThroughAgency(
            descriptor = descriptor,
            adapter = adapter,
            task = probeTask(descriptor),
            prompt = livePrompt(descriptor)
        )

        val state = when (result) {
            is ProviderCallResult.Success -> {
                quotaLedger.recordSuccess(descriptor.id, result.usage.copy(latencyMs = result.usage.latencyMs.coerceAtLeast(1)))
                ProviderActivationState.VERIFIED
            }
            is ProviderCallResult.LocalOnly -> ProviderActivationState.READY
            is ProviderCallResult.Queued -> ProviderActivationState.DEGRADED
            is ProviderCallResult.Failure -> {
                quotaLedger.recordFailure(descriptor.id, result.failure)
                failureState(result.failure, keyLookups.any { it.configured })
            }
        }

        return ProviderActivationRecord(
            providerId = descriptor.id,
            mode = mode,
            state = state,
            descriptorPresent = true,
            adapterStatus = adapterStatus,
            keySources = keyLookups.map { "${it.name}:${it.source}" },
            impact = impact,
            executableSupport = executableSupport,
            fixtureMatrix = fixture,
            verificationSummary = when (result) {
                is ProviderCallResult.Success -> "live success model=${result.model ?: "unknown"}"
                is ProviderCallResult.LocalOnly -> result.content
                is ProviderCallResult.Queued -> result.reason
                is ProviderCallResult.Failure -> result.failure.cleanSummary
            },
            remediation = remediation(state, descriptor, adapterStatus, keyLookups)
        )
    }

    private fun completeThroughAgency(
        descriptor: ProviderDescriptor,
        adapter: ProviderAdapter,
        task: ProviderTask,
        prompt: String
    ): ProviderCallResult {
        val unlockedPaid = descriptor.isPaidLocked() && paidGate.isProviderUnlocked(descriptor.id)
        val proposal = ProviderActionProposals.forCall(
            provider = descriptor.id,
            operation = "activation-live-test",
            promptLength = prompt.length,
            actor = ActionActor.SystemService("provider-activation")
        ).copy(paidProvider = descriptor.isPaidLocked() && !unlockedPaid)
        val decision = agencyGate.evaluate(proposal)
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = descriptor.id,
                    type = NormalizedProviderFailureType.INTERNAL,
                    cleanSummary = "provider activation refused by policy: ${decision.reason}",
                    terminal = true
                )
            )
        }
        return adapter.complete(
            AdapterRequest(
                task = task,
                prompt = prompt,
                context = "Return one short line only.",
                dryRun = false,
                liveNetworkAllowed = environment["ATROPOS_LIVE_PROVIDER_TESTS"] == "1"
            )
        )
    }

    private fun snapshotState(
        descriptor: ProviderDescriptor,
        adapterStatus: atropos.core.provider.adapter.AdapterStatus?,
        keyLookups: List<SecretLookup>,
        fixture: ProviderFixtureMatrixRecord
    ): ProviderActivationState {
        if (descriptor.isPaidLocked() && !paidGate.isProviderUnlocked(descriptor.id)) return ProviderActivationState.LOCKED
        if (descriptor.isLocal && descriptor.hasCapability(ApiCapability.CHAT) && !ollamaProbe()) {
            return ProviderActivationState.OFFLINE
        }
        if (descriptor.isLocal) return ProviderActivationState.READY
        if (adapterStatus == null) return ProviderActivationState.MISSING
        if (adapterStatus.implemented && fixture.passed) return ProviderActivationState.FIXTURE_BACKED
        if (keyLookups.any { it.configured }) return ProviderActivationState.CONFIGURED
        return ProviderActivationState.DRY_RUN_CAPABLE
    }

    private fun offlineSummary(
        adapterStatus: atropos.core.provider.adapter.AdapterStatus?,
        fixture: ProviderFixtureMatrixRecord
    ): String = buildString {
        append("offline verify")
        if (adapterStatus != null) append(" adapter=").append(adapterStatus.health)
        append(" fixtures=").append(fixture.summary())
    }

    private fun remediation(
        state: ProviderActivationState,
        descriptor: ProviderDescriptor,
        adapterStatus: atropos.core.provider.adapter.AdapterStatus?,
        keyLookups: List<SecretLookup>
    ): String =
        when (state) {
            ProviderActivationState.MISSING -> "implement adapter or configure ${descriptor.requiredEnv.joinToString("+").ifBlank { "provider requirements" }}"
            ProviderActivationState.CONFIGURED -> "run /providers verify ${descriptor.id}"
            ProviderActivationState.FIXTURE_BACKED -> if (keyLookups.any { !it.configured }) {
                "configure ${keyLookups.filterNot { it.configured }.joinToString("+") { it.name }} for live verification"
            } else {
                "run /providers live-test ${descriptor.id}"
            }
            ProviderActivationState.DRY_RUN_CAPABLE -> if (adapterStatus?.implemented == true) {
                "run /providers verify ${descriptor.id}"
            } else {
                "provider transport not implemented for ${descriptor.id}"
            }
            ProviderActivationState.LOCKED -> "paid automatic mode remains locked"
            ProviderActivationState.OFFLINE -> "restore local service or network availability"
            ProviderActivationState.INVALID_KEY,
            ProviderActivationState.AUTH_FAILED -> "rotate or correct provider credentials"
            ProviderActivationState.RATE_LIMITED -> "wait for cooldown and retry later"
            ProviderActivationState.QUOTA_EXHAUSTED -> "wait for reset or change free provider"
            ProviderActivationState.BILLING_REQUIRED -> "billing required; provider remains unavailable"
            ProviderActivationState.DEGRADED -> "inspect provider response and fallback route"
            ProviderActivationState.DISABLED -> "provider disabled by policy"
            ProviderActivationState.READY,
            ProviderActivationState.VERIFIED -> "none"
        }

    private fun failureState(failure: ProviderFailure, configured: Boolean): ProviderActivationState =
        when (failure.type) {
            NormalizedProviderFailureType.AUTH_FAILED -> if (configured) ProviderActivationState.INVALID_KEY else ProviderActivationState.AUTH_FAILED
            NormalizedProviderFailureType.RATE_LIMITED -> ProviderActivationState.RATE_LIMITED
            NormalizedProviderFailureType.QUOTA_EXHAUSTED -> ProviderActivationState.QUOTA_EXHAUSTED
            NormalizedProviderFailureType.BILLING_REQUIRED -> ProviderActivationState.BILLING_REQUIRED
            NormalizedProviderFailureType.TIMEOUT,
            NormalizedProviderFailureType.UNAVAILABLE -> ProviderActivationState.OFFLINE
            NormalizedProviderFailureType.MODEL_MISSING,
            NormalizedProviderFailureType.MALFORMED_RESPONSE,
            NormalizedProviderFailureType.EMPTY_RESPONSE,
            NormalizedProviderFailureType.CANCELLED,
            NormalizedProviderFailureType.INTERNAL -> ProviderActivationState.DEGRADED
        }

    private fun livePrompt(descriptor: ProviderDescriptor): String =
        when {
            descriptor.hasCapability(ApiCapability.CHAT) -> "Reply with OK"
            descriptor.hasCapability(ApiCapability.WEB) || descriptor.hasCapability(ApiCapability.READER) -> "example.com"
            descriptor.hasCapability(ApiCapability.ASSET) -> "small local test asset"
            else -> "ATROPOS provider activation test"
        }

    private fun probeTask(descriptor: ProviderDescriptor): ProviderTask =
        when {
            atropos.core.provider.adapter.AssetProviderCatalog.get(descriptor.id) != null ->
                ProviderTask(ProviderTaskKind.ASSET_GENERATION, ApiCapability.ASSET, "local test")
            atropos.core.provider.adapter.DataInfraResearchProviderCatalog.get(descriptor.id)?.schema == atropos.core.provider.adapter.DataInfraProviderSchema.JINA_READER ->
                ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.READER, "https://example.com")
            atropos.core.provider.adapter.DataInfraResearchProviderCatalog.get(descriptor.id)?.schema == atropos.core.provider.adapter.DataInfraProviderSchema.SERPAPI_WEB ->
                ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.WEB, "example search")
            atropos.core.provider.adapter.DataInfraResearchProviderCatalog.get(descriptor.id) != null ->
                ProviderTask(ProviderTaskKind.DATABASE_STATE, descriptor.capabilities.first(), "data")
            atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == atropos.core.provider.adapter.NonOpenAiProviderSchema.CLOUDFLARE_WORKERS ->
                ProviderTask(ProviderTaskKind.EDGE_WORKER, ApiCapability.EDGE, "edge")
            descriptor.hasCapability(ApiCapability.CHAT) -> ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello")
            descriptor.hasCapability(ApiCapability.CODE) -> ProviderTask(ProviderTaskKind.FAST_CODE_DRAFT, ApiCapability.CODE, "fun ok() = 1")
            descriptor.hasCapability(ApiCapability.READER) -> ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.READER, "https://example.com")
            descriptor.hasCapability(ApiCapability.WEB) -> ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.WEB, "example search")
            descriptor.hasCapability(ApiCapability.ASSET) -> ProviderTask(ProviderTaskKind.ASSET_GENERATION, ApiCapability.ASSET, "local test")
            descriptor.hasCapability(ApiCapability.VECTOR_DB) -> ProviderTask(ProviderTaskKind.VECTOR_MEMORY, ApiCapability.VECTOR_DB, "vector")
            descriptor.hasCapability(ApiCapability.DATABASE) -> ProviderTask(ProviderTaskKind.DATABASE_STATE, ApiCapability.DATABASE, "db")
            descriptor.hasCapability(ApiCapability.EDGE) -> ProviderTask(ProviderTaskKind.EDGE_WORKER, ApiCapability.EDGE, "edge")
            descriptor.hasCapability(ApiCapability.STORAGE) -> ProviderTask(ProviderTaskKind.DATABASE_STATE, ApiCapability.STORAGE, "storage")
            descriptor.hasCapability(ApiCapability.SECRET) -> ProviderTask(ProviderTaskKind.SECRET_STORAGE, ApiCapability.SECRET, "secret")
            else -> ProviderTask(ProviderTaskKind.LOCAL_ONLY, ApiCapability.LOCAL_TOOL, "local")
        }
}
