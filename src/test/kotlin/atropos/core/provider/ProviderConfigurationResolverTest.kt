package atropos.core.provider

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import kotlin.test.Test
import kotlin.test.assertTrue

class ProviderConfigurationResolverTest {
    @Test
    fun truth_resolver_accepts_namespace_alias_without_process_environment() {
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig("/tmp/atropos-test", "/tmp/atropos-test/vector.db"),
            RuntimeConfig("groq", 0.2)
        )
        val resolver = ProviderConfigurationResolver(
            config = config,
            environment = mapOf("ATROPOS_PROVIDER_GEMINI_API_KEY" to "namespace-secret")
        )

        assertTrue(resolver.isConfigured(StaticProviderDescriptorRegistry().getById("gemini")!!))
    }
}
