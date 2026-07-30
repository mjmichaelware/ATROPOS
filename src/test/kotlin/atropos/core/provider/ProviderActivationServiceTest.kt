package atropos.core.provider

import atropos.core.paid.EmergencyPaidGate
import atropos.core.provider.adapter.AdapterStatus
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import atropos.core.security.MapSecretSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

class ProviderActivationServiceTest {
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
}
