package atropos.core.provider

import atropos.core.provider.adapter.AssetProviderCatalog
import atropos.core.provider.adapter.AssetProviderFixtures
import atropos.core.provider.adapter.DataInfraKernelFixtures
import atropos.core.provider.adapter.DataInfraResearchProviderCatalog
import atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog
import atropos.core.provider.adapter.NonOpenAiKernelFixtures
import atropos.core.provider.adapter.AdapterKernelFixtures
import atropos.core.provider.adapter.AdapterRequest
import atropos.core.provider.adapter.AdapterStatus
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog
import atropos.core.provider.adapter.ProviderAdapter
import atropos.core.provider.adapter.ProviderAdapterRegistry
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderFixtureMatrixServiceTest {
    @Test
    fun openai_descriptor_consumes_the_discovered_base_endpoint_override() {
        val spec = OpenAiCompatibleProviderCatalog.get("openai")

        assertEquals("OPENAI_API_BASE", spec?.endpointEnv)
    }

    @Test
    fun gemini_native_adapter_accepts_google_api_key_alias() {
        val registry = StaticProviderDescriptorRegistry()
        val adapter = StaticProviderAdapterRegistry(
            registry,
            env = mapOf("GOOGLE_API_KEY" to "fixture-secret")
        ).getByProviderId("gemini")

        assertTrue(adapter?.status()?.configured == true)
    }

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
        OpenAiCompatibleProviderCatalog.all().forEach { spec ->
            assertFixtureNames(spec.providerId, AdapterKernelFixtures.runAll(spec.providerId), required)
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

    @Test
    fun no_adapters_are_missing_normalized_fixtures() {
        val registry = StaticProviderDescriptorRegistry()
        val service = ProviderFixtureMatrixService(
            registry = registry,
            adapterRegistry = StaticProviderAdapterRegistry(registry, env = emptyMap())
        )
        val missing = service.listAdaptersMissingNormalizedFixtures()
        assertTrue(missing.isEmpty(), "Adapters missing normalized fixtures: $missing")
    }

    @Test
    fun generic_adapter_fixture_branch_is_explicitly_offline() {
        val descriptor = ProviderDescriptor(
            id = "fixture-only",
            displayName = "Fixture Only",
            costMode = CostMode.FREE,
            quotaTier = 1,
            capabilities = setOf(ApiCapability.CHAT)
        )
        val requests = mutableListOf<AdapterRequest>()
        val adapter = object : ProviderAdapter {
            override val descriptor = descriptor
            override fun status() = AdapterStatus(
                providerId = descriptor.id,
                implemented = true,
                configured = false,
                dryRunOnly = true,
                modelCount = 1,
                health = "fixture",
                detail = "offline fixture"
            )

            override fun complete(request: AdapterRequest): ProviderCallResult {
                requests += request
                return ProviderCallResult.LocalOnly(request.task, "fixture")
            }
        }
        val registry = object : ProviderDescriptorRegistry {
            override fun getAll() = listOf(descriptor)
            override fun getById(id: String) = getAll().firstOrNull { it.id == id }
            override fun getFreeEligible() = getAll()
            override fun getPaidLocked() = emptyList<ProviderDescriptor>()
            override fun getByCapability(capability: ApiCapability) = getAll().filter { capability in it.capabilities }
        }
        val adapters = object : ProviderAdapterRegistry {
            override fun getAll() = listOf(adapter)
            override fun getByProviderId(providerId: String) = adapter.takeIf { it.providerId == providerId }
            override fun getByCapability(capability: ApiCapability) = listOf(adapter).filter { capability in it.capabilities }
            override fun status() = listOf(adapter.status())
        }

        val result = ProviderFixtureMatrixService(registry, adapters).runProvider(descriptor.id)

        assertTrue(result.passed, result.details.joinToString(","))
        assertTrue(requests.isNotEmpty())
        assertTrue(requests.all { it.dryRun && !it.liveNetworkAllowed })
    }
}
