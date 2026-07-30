package atropos.core.provider

import atropos.core.provider.adapter.AssetProviderCatalog
import atropos.core.provider.adapter.AssetProviderFixtures
import atropos.core.provider.adapter.DataInfraKernelFixtures
import atropos.core.provider.adapter.DataInfraResearchProviderCatalog
import atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog
import atropos.core.provider.adapter.NonOpenAiKernelFixtures
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderFixtureMatrixServiceTest {
    @Test
    fun fixture_matrix_passes_offline_for_all_registered_providers() {
        val registry = StaticProviderDescriptorRegistry()
        val service = ProviderFixtureMatrixService(
            registry = registry,
            adapterRegistry = StaticProviderAdapterRegistry(registry, env = emptyMap())
        )

        val results = service.runAll()
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.passed }, results.filterNot { it.passed }.joinToString("\n") { "${it.providerId}: ${it.details.joinToString(",")}" })
        results.forEach { result ->
            val names = result.details.map { it.substringBefore("=") }.toSet()
            assertTrue(names.containsAll(setOf(
                "success",
                "dry_run",
                "auth_failed",
                "rate_limited",
                "billing_required",
                "unavailable",
                "timeout",
                "malformed_response",
                "empty_response",
                "cancellation",
                "redaction",
                "attestation"
            )), "${result.providerId}: ${result.details}")
        }
    }

    @Test
    fun family_fixtures_cover_required_failure_outcomes_before_matrix_rollup() {
        val required = setOf(
            "success",
            "provider_error_auth",
            "provider_error_rate_limit",
            "provider_error_billing",
            "unavailable",
            "timeout",
            "malformed",
            "empty",
            "cancelled"
        )

        NonOpenAiFreeProviderCatalog.all().forEach { spec ->
            assertFixtureNames(spec.providerId, NonOpenAiKernelFixtures.runAll(spec.providerId), required)
        }
        DataInfraResearchProviderCatalog.all().forEach { spec ->
            assertFixtureNames(spec.providerId, DataInfraKernelFixtures.runAll(spec.providerId), required)
        }
        AssetProviderCatalog.all().forEach { spec ->
            assertFixtureNames(spec.providerId, AssetProviderFixtures.runAll(spec.providerId), required)
        }
    }

    private fun assertFixtureNames(
        providerId: String,
        fixtures: List<atropos.core.provider.adapter.AdapterFixtureResult>,
        required: Set<String>
    ) {
        val names = fixtures.map { it.fixture }.toSet()
        assertTrue(names.containsAll(required), "$providerId missing ${required - names}: $fixtures")
        assertTrue(fixtures.all { it.passed }, "$providerId failed ${fixtures.filterNot { it.passed }}")
    }
}
