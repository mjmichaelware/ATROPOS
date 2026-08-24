package atropos.core.provider

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import atropos.core.security.TokenIsolationVault

class ProviderOnboardingTest {
    @Test
    fun render_reports_discovered_healthy_and_disabled_counts() {
        val service = ProviderOnboardingService(
            root = Files.createTempDirectory("provider-onboarding-render"),
            environment = mapOf("GROQ_API_KEY" to "secret")
        )
        service.refresh()
        assertTrue(service.render().contains(Regex("discovered=\\d+ healthy=\\d+ disabled=\\d+")))
    }

    @Test
    fun discovery_accepts_common_aliases_without_persisting_values() {
        val root = Files.createTempDirectory("provider-onboarding")
        val service = ProviderOnboardingService(
            root = root,
            environment = mapOf("CLAUDE_API_KEY" to "secret-value", "GROK_TOKEN" to "another-secret")
        )
        val records = service.refresh().associateBy { it.providerId }
        assertEquals(CheapProviderHealth.HEALTHY, records.getValue("anthropic").health)
        assertEquals(CheapProviderHealth.HEALTHY, records.getValue("xai").health)
        assertTrue(Files.readString(root.resolve(".atropos/provider/providers.json")).contains("CLAUDE_API_KEY"))
        assertTrue(!Files.readString(root.resolve(".atropos/provider/providers.json")).contains("secret-value"))
    }

    @Test
    fun discovery_accepts_atropos_provider_namespace_and_classifies_billing() {
        val root = Files.createTempDirectory("provider-onboarding-prefix")
        val records = ProviderOnboardingService(
            root = root,
            environment = mapOf("ATROPOS_PROVIDER_OPENAI_API_KEY" to "secret")
        ).refresh().associateBy { it.providerId }
        assertEquals(CheapProviderHealth.HEALTHY, records.getValue("openai").health)
        assertEquals(BillingClass.PAID, StaticProviderDescriptorRegistry().getById("openai")!!.billingClass())
        assertEquals(BillingClass.LOCAL, StaticProviderDescriptorRegistry().getById("ollama")!!.billingClass())
    }

    @Test
    fun discovery_accepts_aws_bedrock_credentials_without_registering_a_new_adapter() {
        val root = Files.createTempDirectory("provider-onboarding-aws")
        val records = ProviderOnboardingService(
            root = root,
            environment = mapOf(
                "AWS_ACCESS_KEY_ID" to "access",
                "AWS_SECRET_ACCESS_KEY" to "secret",
                "AWS_REGION" to "us-east-1"
            )
        ).refresh().associateBy { it.providerId }
        assertEquals(CheapProviderHealth.HEALTHY, records.getValue("aws_bedrock").health)
        assertTrue(records.getValue("aws_bedrock").matchedEnvNames.contains("AWS_REGION"))
    }

    @Test
    fun endpoint_metadata_does_not_count_as_a_key_without_credentials() {
        val root = Files.createTempDirectory("provider-onboarding-endpoint")
        val endpointOnly = ProviderOnboardingService(
            root = root,
            environment = mapOf("OPENAI_API_BASE" to "https://example.invalid")
        ).refresh().associateBy { it.providerId }
        assertEquals(CheapProviderHealth.UNTESTED, endpointOnly.getValue("openai").health)
        assertTrue(endpointOnly.getValue("openai").matchedEnvNames.contains("OPENAI_API_BASE"))

        val azure = ProviderOnboardingService(
            root = Files.createTempDirectory("provider-onboarding-azure"),
            environment = mapOf(
                "AZURE_OPENAI_ENDPOINT" to "https://example.invalid",
                "AZURE_OPENAI_API_KEY" to "secret"
            )
        ).refresh().first { it.providerId == "azure_openai" }
        assertEquals(CheapProviderHealth.HEALTHY, azure.health)
    }

    @Test
    fun malformed_credential_shape_is_unhealthy_without_rendering_the_value() {
        val root = Files.createTempDirectory("provider-onboarding-malformed")
        val secret = "not-rendered\nsecond-line"
        val service = ProviderOnboardingService(
            root = root,
            environment = mapOf("GROQ_API_KEY" to secret)
        )

        val record = service.refresh().first { it.providerId == "groq" }

        assertEquals(CheapProviderHealth.UNHEALTHY, record.health)
        val persisted = Files.readString(root.resolve(".atropos/provider/providers.json"))
        assertTrue(!persisted.contains(secret))
        assertTrue(!service.render().contains("second-line"))
    }

    @Test
    fun generic_namespace_matches_multiword_provider_and_preference_changes_order() {
        val root = Files.createTempDirectory("provider-onboarding-preference")
        val service = ProviderOnboardingService(
            root = root,
            environment = mapOf(
                "ATROPOS_PROVIDER_DEEPSEEK_API_KEY" to "secret",
                "GROQ_API_KEY" to "secret"
            )
        )
        service.refresh()
        service.prefer("deepseek_direct")
        val ordered = service.list().filter { it.health == CheapProviderHealth.HEALTHY }
        assertEquals(listOf("deepseek_direct", "groq"), ordered.map { it.providerId })
        assertTrue(ordered.first().matchedEnvNames.contains("ATROPOS_PROVIDER_DEEPSEEK_API_KEY"))
    }

    @Test
    fun discovered_together_and_fireworks_are_wired_to_paid_adapters() {
        val registry = StaticProviderDescriptorRegistry()
        val records = ProviderOnboardingService(
            root = Files.createTempDirectory("provider-onboarding-catalog"),
            environment = mapOf(
                "TOGETHER_API_KEY" to "together-secret",
                "FIREWORKS_API_KEY" to "fireworks-secret"
            )
        ).refresh().associateBy { it.providerId }
        assertEquals(CheapProviderHealth.HEALTHY, records.getValue("together").health)
        assertEquals(CheapProviderHealth.HEALTHY, records.getValue("fireworks").health)
        assertEquals(BillingClass.PAID, registry.getById("together")!!.billingClass())
        assertEquals(BillingClass.PAID, registry.getById("fireworks")!!.billingClass())
        val adapters = atropos.core.provider.adapter.StaticProviderAdapterRegistry(registry, emptyMap())
        assertTrue(adapters.getByProviderId("together")!!.status().implemented)
        assertTrue(adapters.getByProviderId("fireworks")!!.status().implemented)
    }

    @Test
    fun azure_discovery_uses_endpoint_bound_compatible_adapter_without_network_call() {
        val registry = StaticProviderDescriptorRegistry()
        val environment = mapOf(
            "AZURE_OPENAI_API_KEY" to "azure-secret",
            "AZURE_OPENAI_ENDPOINT" to "https://example.invalid/openai/deployments/test/chat/completions?api-version=2024-06-01"
        )
        val service = ProviderOnboardingService(
            root = Files.createTempDirectory("provider-onboarding-azure-adapter"),
            environment = environment
        )
        assertEquals(CheapProviderHealth.HEALTHY, service.refresh().first { it.providerId == "azure_openai" }.health)
        val adapter = atropos.core.provider.adapter.StaticProviderAdapterRegistry(registry, environment)
            .getByProviderId("azure_openai")!!
        assertTrue(adapter.status().implemented)
        assertTrue(adapter.status().configured)
    }

    @Test
    fun adapter_registry_resolves_common_alias_without_logging_or_network() {
        val registry = StaticProviderDescriptorRegistry()
        val adapter = atropos.core.provider.adapter.StaticProviderAdapterRegistry(
            registry,
            mapOf("CLAUDE_API_KEY" to "alias-secret")
        ).getByProviderId("anthropic")!!
        assertTrue(adapter.status().configured)
        assertTrue(adapter.status().implemented)
    }

    @Test
    fun preferred_provider_is_exposed_in_route_order_without_overriding_cost_policy() {
        val root = Files.createTempDirectory("provider-preference-route")
        val service = ProviderOnboardingService(
            root = root,
            environment = mapOf("GROQ_API_KEY" to "groq", "GEMINI_API_KEY" to "gemini")
        )
        service.refresh()
        service.prefer("gemini")
        assertEquals(listOf("gemini"), service.preferredProviderIds().filter { it in setOf("gemini", "groq") })
    }

    @Test
    fun paid_provider_requires_approval_and_free_cascade_stays_free_first() {
        val gate = ProviderPolicyGate(healthy = { setOf("openai", "groq", "gemini") })
        assertTrue(gate.freeCascade(ApiCapability.CHAT).all { it.isFreeEligible() })
        assertTrue(gate.paidApproval(ApiCapability.CHAT, "free cascade exhausted") != null)
        val failure = runCatching { gate.requirePaidApproval("openai") }.exceptionOrNull()
        assertTrue(failure?.message?.contains("explicit approval") == true)
    }

    @Test
    fun connect_writes_only_to_the_local_vault_and_refreshes_from_it() {
        val root = Files.createTempDirectory("provider-connect")
        val service = ProviderOnboardingService(root = root, environment = emptyMap())
        service.connectToVault("groq", "test-secret")
        assertEquals("test-secret", TokenIsolationVault(root.resolve(".atropos/secrets")).readSecret("GROQ_API_KEY"))
        val persisted = Files.readString(root.resolve(".atropos/provider/providers.json"))
        assertTrue(!persisted.contains("test-secret"))
        assertTrue(service.refresh().first { it.providerId == "groq" }.matchedEnvNames.contains("GROQ_API_KEY"))
    }
}
