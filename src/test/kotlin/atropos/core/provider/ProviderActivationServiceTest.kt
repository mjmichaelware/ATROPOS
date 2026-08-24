package atropos.core.provider

import atropos.core.AtroposRepoRootLocator
import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.adapter.AdapterStatus
import atropos.core.provider.adapter.AdapterRequest
import atropos.core.provider.adapter.ProviderAdapter
import atropos.core.provider.adapter.ProviderAdapterRegistry
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import atropos.core.security.MapSecretSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class ProviderActivationServiceTest {
    @Test
    fun activation_store_default_root_is_under_atropos_root() {
        assertEquals(
            AtroposRepoRootLocator.resolve().resolve(".atropos/provider/activation"),
            ProviderActivationStore.defaultRoot()
        )
    }

    @Test
    fun verify_marks_configured_free_transport_as_verified_offline() {
        val temp = Files.createTempDirectory("atropos-provider-verify")
        val registry = StaticProviderDescriptorRegistry()
        val env = mapOf("GROQ_API_KEY" to "test-groq-key")
        val adapterRegistry = StaticProviderAdapterRegistry(registry, env)
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = adapterRegistry,
            secretSource = MapSecretSource(env),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, adapterRegistry),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false }
        )

        val record = service.verify("groq")
        assertEquals(ProviderActivationState.VERIFIED, record.state)
        assertTrue(record.fixtureMatrix?.passed == true)
    }

    @Test
    fun live_test_refuses_paid_locked_provider_without_network_call() {
        val temp = Files.createTempDirectory("atropos-provider-live")
        val registry = StaticProviderDescriptorRegistry()
        val adapterRegistry = StaticProviderAdapterRegistry(registry, emptyMap())
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = adapterRegistry,
            secretSource = MapSecretSource(emptyMap()),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, adapterRegistry),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false }
        )

        val record = service.liveTest("openai")
        assertEquals(ProviderActivationState.LOCKED, record.state)
        assertTrue(record.verificationSummary.contains("refused"))
    }

    @Test
    fun live_test_refuses_credit_pool_provider_before_transport() {
        val temp = Files.createTempDirectory("atropos-provider-credit-pool-live")
        val registry = StaticProviderDescriptorRegistry()
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = StaticProviderAdapterRegistry(registry, emptyMap()),
            secretSource = MapSecretSource(emptyMap()),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, StaticProviderAdapterRegistry(registry, emptyMap())),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false }
        )

        val record = service.liveTest("cerebras")

        assertEquals(ProviderActivationState.LOCKED, record.state)
        assertTrue(record.verificationSummary.contains("refused"))
    }

    @Test
    fun verify_reports_missing_service_provider_execution_requirement() {
        val temp = Files.createTempDirectory("atropos-service-provider-missing")
        val registry = StaticProviderDescriptorRegistry()
        val adapterRegistry = StaticProviderAdapterRegistry(registry, emptyMap())
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = adapterRegistry,
            secretSource = MapSecretSource(emptyMap()),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, adapterRegistry),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false }
        )

        val record = service.verify("google_drive")
        assertEquals(ProviderActivationState.FIXTURE_BACKED, record.state)
        assertTrue(record.remediation.contains("GOOGLE_APPLICATION_CREDENTIALS"))
        assertTrue(record.impact.contains("storage"))
    }

    @Test
    fun activation_records_render_every_canonical_state_with_truth_fields() {
        val adapterStatus = AdapterStatus(
            providerId = "fixture-provider",
            implemented = true,
            configured = true,
            dryRunOnly = false,
            modelCount = 1,
            health = "ready",
            detail = "fixture adapter state"
        )

        ProviderActivationState.entries.forEach { state ->
            val record = ProviderActivationRecord(
                providerId = "fixture-provider",
                mode = ProviderVerificationMode.VERIFY,
                state = state,
                descriptorPresent = true,
                adapterStatus = adapterStatus,
                keySources = listOf("FIXTURE_ACCESS_IDENTIFIER:explicit"),
                impact = listOf("chat", "code"),
                executableSupport = true,
                fixtureMatrix = ProviderFixtureMatrixRecord(
                    providerId = "fixture-provider",
                    passed = true,
                    passedCount = 2,
                    totalCount = 2,
                    details = listOf("success=PASS", "redaction=PASS")
                ),
                verificationSummary = "verification for ${state.name.lowercase()}",
                remediation = "remediation for ${state.name.lowercase()}"
            )

            val rendered = record.render()
            assertTrue(rendered.contains("state: ${state.name.lowercase()}"), state.name)
            assertTrue(rendered.contains("key sources: FIXTURE_ACCESS_IDENTIFIER:explicit"), state.name)
            assertTrue(rendered.contains("impact: chat,code"), state.name)
            assertTrue(rendered.contains("adapter implemented: yes"), state.name)
            assertTrue(rendered.contains("adapter configured: yes"), state.name)
            assertTrue(rendered.contains("verification: verification for ${state.name.lowercase()}"), state.name)
            assertTrue(rendered.contains("remediation: remediation for ${state.name.lowercase()}"), state.name)
        }
    }

    @Test
    fun remote_provider_never_ready_on_descriptor_or_key_alone_without_verification() {
        val temp = Files.createTempDirectory("atropos-provider-never-ready")
        val registry = StaticProviderDescriptorRegistry()
        val env = mapOf("GROQ_API_KEY" to "test-groq-key")
        val adapterRegistry = StaticProviderAdapterRegistry(registry, env)
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = adapterRegistry,
            secretSource = MapSecretSource(env),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, adapterRegistry),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false }
        )

        val record = service.snapshot("groq")
        assertTrue(record.state == ProviderActivationState.CONFIGURED || record.state == ProviderActivationState.FIXTURE_BACKED)
    }

    @Test
    fun live_provider_probe_requires_explicit_network_opt_in() {
        val temp = Files.createTempDirectory("atropos-provider-live-opt-in")
        val registry = StaticProviderDescriptorRegistry()
        val env = mapOf("GROQ_API_KEY" to "test-groq-key")
        val adapterRegistry = StaticProviderAdapterRegistry(registry, env)
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = adapterRegistry,
            secretSource = MapSecretSource(env),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, adapterRegistry),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false },
            environment = emptyMap()
        )

        val record = service.liveTest("groq")

        assertEquals(ProviderActivationState.DEGRADED, record.state)
        assertTrue(record.verificationSummary.contains("requires ATROPOS_LIVE_PROVIDER_TESTS"))
    }

    @Test
    fun live_test_converts_adapter_exception_to_unhealthy_record_without_crashing() {
        val temp = Files.createTempDirectory("atropos-provider-live-failure")
        val registry = StaticProviderDescriptorRegistry()
        val onboarding = ProviderOnboardingService(
            root = temp.resolve("onboarding"),
            environment = mapOf("GROQ_API_KEY" to "test-groq-key")
        )
        onboarding.refresh()
        val groqDescriptor = registry.getById("groq") ?: error("groq descriptor missing")
        val throwingAdapter = object : ProviderAdapter {
            override val descriptor = groqDescriptor

            override fun status() = AdapterStatus(
                providerId = groqDescriptor.id,
                implemented = true,
                configured = true,
                dryRunOnly = false,
                modelCount = 1,
                health = "ready",
                detail = "injected failure fixture"
            )

            override fun complete(request: AdapterRequest): ProviderCallResult {
                error("connection refused by injected transport")
            }
        }
        val adapters = object : ProviderAdapterRegistry {
            override fun getAll() = listOf(throwingAdapter)
            override fun getByProviderId(providerId: String) = throwingAdapter.takeIf { it.providerId == providerId }
            override fun getByCapability(capability: ApiCapability) =
                listOf(throwingAdapter).filter { capability in it.capabilities }
            override fun status() = listOf(throwingAdapter.status())
        }
        val service = ProviderActivationService(
            registry = registry,
            adapterRegistry = adapters,
            secretSource = MapSecretSource(mapOf("GROQ_API_KEY" to "test-groq-key")),
            quotaLedger = FileQuotaLedger(temp.resolve("quota.tsv").toFile(), FileQuotaLedger.seedFromDescriptors(registry)),
            fixtureMatrix = ProviderFixtureMatrixService(registry, StaticProviderAdapterRegistry(registry, emptyMap())),
            store = ProviderActivationStore(temp.resolve("activation")),
            paidGate = EmergencyPaidGate(temp.resolve("paid").toFile()),
            ollamaProbe = { false },
            environment = mapOf("ATROPOS_LIVE_PROVIDER_TESTS" to "1"),
            liveTestHealthReporter = { providerId, healthy -> onboarding.recordLiveTest(providerId, healthy) }
        )

        val record = service.liveTest("groq")

        assertEquals(ProviderActivationState.OFFLINE, record.state)
        assertTrue(record.verificationSummary.contains("unavailable"))
        assertTrue(onboarding.healthyProviderIds().isEmpty())
        assertEquals(ProviderActivationState.OFFLINE, service.liveTest("groq").state)
    }
}
