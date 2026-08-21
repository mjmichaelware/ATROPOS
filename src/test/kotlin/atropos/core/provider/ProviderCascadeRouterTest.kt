package atropos.core.provider

import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.AIProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderCascadeRouterTest {
    @Test
    fun exposes_the_documented_chain_through_the_canonical_router() {
        val router = ProviderCascadeRouter(ProviderFactory())
        assertEquals(FallbackChain.CHAT, router.declaredFallbackChain(ApiCapability.CHAT))
    }

    @Test
    fun response_acceptance_rejects_provider_one_and_uses_provider_two() {
        val attempts = mutableListOf<String>()
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                attempts += provider
                object : AIProvider {
                    override val name: String = provider
                    override fun complete(prompt: String, context: String): String =
                        if (provider == "provider-one") "not-an-enum" else "STATE_MODEL"
                }
            }
        )

        val result = router.completeWithCascade(
            requestedProvider = "provider-one",
            prompt = "classify",
            context = "",
            providerOrderOverride = listOf("provider-one", "provider-two"),
            acceptResponse = { it == "STATE_MODEL" }
        )

        assertEquals("provider-two", result.providerName)
        assertEquals("STATE_MODEL", result.response)
        assertEquals(listOf("provider-one", "provider-two"), attempts)
        assertTrue(result.errors.any { it.provider == "provider-one" && it.type == atropos.core.FailureType.INVALID_RESPONSE })
    }

    @Test
    fun exhausted_dimension_cascade_reports_every_attempt_and_queues() {
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                throw IllegalStateException("$provider API key is missing")
            }
        )

        val result = router.completeWithCascade(
            requestedProvider = "provider-one",
            prompt = "classify",
            context = "",
            providerOrderOverride = listOf("provider-one", "provider-two"),
            acceptResponse = { response -> response == "STATE_MODEL" }
        )

        assertTrue(result.queued)
        assertEquals(listOf("provider-one", "provider-two"), result.errors.map { it.provider })
        assertTrue(result.errors.all { it.type == atropos.core.FailureType.MISSING_KEY })
        assertTrue(result.queueReason.orEmpty().contains("missing API key"))
    }
}
