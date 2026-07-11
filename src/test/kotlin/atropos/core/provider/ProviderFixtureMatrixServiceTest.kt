package atropos.core.provider

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
    }
}
