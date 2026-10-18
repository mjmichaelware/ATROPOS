/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.provider.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.AIProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import atropos.core.paid.EmergencyPaidGate

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
        // Strike 1 on provider-one fails, strike 2 retried on provider-one (no progress), then cascades to provider-two
        assertEquals(listOf("provider-one", "provider-one", "provider-two"), attempts)
        assertTrue(result.errors.any { it.provider == "provider-one" && it.type == atropos.core.FailureType.INVALID_RESPONSE })
    }

    @Test
    fun blank_response_falls_back_to_the_next_provider_by_default() {
        val attempts = mutableListOf<String>()
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                attempts += provider
                object : AIProvider {
                    override val name: String = provider
                    override fun complete(prompt: String, context: String): String =
                        if (provider == "provider-one") "" else "hello from provider two"
                }
            }
        )

        val result = router.completeWithCascade(
            requestedProvider = "provider-one",
            prompt = "hi",
            context = "",
            providerOrderOverride = listOf("provider-one", "provider-two")
        )

        assertEquals("provider-two", result.providerName)
        assertEquals("hello from provider two", result.response)
        assertEquals(listOf("provider-one", "provider-one", "provider-two"), attempts)
        assertTrue(
            result.errors.any {
                it.provider == "provider-one" && it.type == atropos.core.FailureType.INVALID_RESPONSE
            }
        )
    }

    @Test
    fun mathematical_progress_grants_third_retry_strike_before_success() {
        var callCount = 0
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                object : AIProvider {
                    override val name: String = provider
                    override fun complete(prompt: String, context: String): String {
                        callCount++
                        return when (callCount) {
                            1 -> "" // Strike 1: blank / fail
                            2 -> "fun partial() {" // Strike 2: valid code + AST depth > 0 (mathematical progress > 0)
                            else -> "fun partial() { val complete = true }" // Strike 3: complete!
                        }
                    }
                }
            }
        )

        val result = router.completeWithCascade(
            requestedProvider = "provider-one",
            prompt = "generate code",
            context = "",
            providerOrderOverride = listOf("provider-one", "provider-two"),
            acceptResponse = { it.contains("complete") }
        )

        assertEquals("provider-one", result.providerName)
        assertEquals(3, callCount)
        assertTrue(result.response.contains("complete = true"))
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

    @Test
    fun free_exhaustion_surfaces_paid_approval_instead_of_spending() {
        val paidGate = EmergencyPaidGate(Files.createTempDirectory("paid-approval").toFile())
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { throw IllegalStateException("free provider unavailable") },
            healthyProviderIds = { setOf("groq", "openai") },
            localOnly = { false },
            paidGate = paidGate
        )
        val result = router.completeWithCascade(
            requestedProvider = "groq",
            prompt = "answer",
            context = "",
            providerOrderOverride = listOf("groq", "openai")
        )
        assertTrue(result.paidApproval != null)
        assertTrue(!result.queued)
        assertEquals("paid_approval_required", result.providerName)
        assertTrue(result.queueReason.orEmpty().contains("PAID APPROVAL REQUIRED"))
    }

    @Test
    fun unlocked_paid_provider_can_resume_the_same_cascade() {
        val paidGate = EmergencyPaidGate(Files.createTempDirectory("paid-resume").toFile())
        paidGate.unlock("openai", "1m", "operator approved this session")
        val attempts = mutableListOf<String>()
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                attempts += provider
                if (provider == "openai") object : AIProvider {
                    override val name: String = provider
                    override fun complete(prompt: String, context: String): String = "approved"
                } else throw IllegalStateException("free provider unavailable")
            },
            healthyProviderIds = { setOf("groq", "openai") },
            localOnly = { false },
            paidGate = paidGate
        )
        val result = router.completeWithCascade(
            requestedProvider = "groq",
            prompt = "answer",
            context = "",
            providerOrderOverride = listOf("groq", "openai")
        )
        assertEquals("openai", result.providerName)
        assertEquals("approved", result.response)
        assertEquals(listOf("groq", "groq", "openai"), attempts)
    }

    @Test
    fun deepseek_is_present_in_active_cascade_and_non_chat_endpoints_are_isolated() {
        val router = ProviderCascadeRouter(factory = ProviderFactory())
        val order = router.providerOrderPreview("groq")
        assertTrue(order.contains("deepseek"), "deepseek must be in active cascade sequence")
        assertTrue(!order.contains("jina"), "jina must not be in active chat cascade")
        assertTrue(!order.contains("pinecone"), "pinecone must not be in active chat cascade")
    }

    @Test
    fun isolates_non_chat_endpoints_when_in_override_list() {
        val router = ProviderCascadeRouter(factory = ProviderFactory())
        val order = router.providerOrderPreview("groq", providerOrderOverride = listOf("groq", "jina", "pinecone", "deepseek"))
        assertTrue(order.contains("groq"))
        assertTrue(order.contains("deepseek"))
        assertTrue(!order.contains("jina"))
        assertTrue(!order.contains("pinecone"))
    }

    @Test
    fun gracefully_falls_back_immediately_on_http_402_and_404_without_burning_retries() {
        val attempts = mutableListOf<String>()
        val router = ProviderCascadeRouter(
            factory = ProviderFactory(),
            providerResolver = { provider ->
                attempts += provider
                if (provider == "provider-402") {
                    throw RuntimeException("HTTP 402 Payment Required: insufficient credits")
                } else {
                    object : AIProvider {
                        override val name: String = provider
                        override fun complete(prompt: String, context: String): String = "fallback success"
                    }
                }
            }
        )

        val result = router.completeWithCascade(
            requestedProvider = "provider-402",
            prompt = "fix",
            context = "",
            providerOrderOverride = listOf("provider-402", "provider-fallback")
        )

        assertEquals("provider-fallback", result.providerName)
        assertEquals("fallback success", result.response)
        // Verify provider-402 was only called ONCE and immediately skipped (zero wasted retries)
        assertEquals(listOf("provider-402", "provider-fallback"), attempts)
    }
}
