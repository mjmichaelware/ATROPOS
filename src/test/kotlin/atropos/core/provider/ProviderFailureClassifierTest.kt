/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Source Doc 2 §.300 §7 names seven failure transitions and each implies a
 * different wait. Collapsing them produces two opposite bugs at once — a
 * billing failure retried forever and a rate limit abandoned after one attempt
 * — and both look identical in a log.
 */
class ProviderFailureClassifierTest {

    private val now = 1_000_000L
    private val classifier = ProviderFailureClassifier(nowEpochMs = { now })

    private fun record(state: ProviderAvailabilityState = ProviderAvailabilityState.READY) =
        ProviderQuotaRecord(
            providerId = "groq",
            costMode = CostMode.FREE,
            quotaWeight = 1,
            configured = true,
            verified = true,
            state = state,
            successScore = 1.0
        )

    private fun failure(type: NormalizedProviderFailureType, retryAfterMs: Long? = null, resetAt: Long? = null) =
        ProviderFailure("groq", type, "groq $type", retryAfterMs = retryAfterMs, resetAtEpochMs = resetAt)

    @Test
    fun `a rate limit sets a cooldown from the retry-after header`() {
        val after = classifier.classify(
            record(),
            failure(NormalizedProviderFailureType.RATE_LIMITED),
            headers = mapOf("Retry-After" to "120")
        )

        assertEquals(ProviderAvailabilityState.COOLDOWN, after.state)
        assertEquals(now + 120_000, after.cooldownUntilEpochMs)
    }

    @Test
    fun `a rate limit without a header falls back to a default rather than guessing`() {
        val after = classifier.classify(record(), failure(NormalizedProviderFailureType.RATE_LIMITED))

        assertEquals(ProviderAvailabilityState.COOLDOWN, after.state)
        assertEquals(now + 60_000, after.cooldownUntilEpochMs)
    }

    @Test
    fun `an unparseable retry-after is ignored rather than misread`() {
        val after = classifier.classify(
            record(),
            failure(NormalizedProviderFailureType.RATE_LIMITED),
            headers = mapOf("Retry-After" to "Wed, 21 Oct 2026 07:28:00 GMT")
        )

        assertEquals(now + 60_000, after.cooldownUntilEpochMs, "an HTTP-date must not become a cooldown of decades")
    }

    @Test
    fun `quota exhaustion records the reset the provider stated`() {
        val after = classifier.classify(
            record(),
            failure(NormalizedProviderFailureType.QUOTA_EXHAUSTED, resetAt = now + 3_600_000)
        )

        assertEquals(ProviderAvailabilityState.EXHAUSTED_UNTIL_RESET, after.state)
        assertEquals(now + 3_600_000, after.resetAtEpochMs)
    }

    /**
     * The strongest rule in §7. A cooldown here would let a long-enough wait
     * retry a card that was declined.
     */
    @Test
    fun `a billing failure never auto-retries`() {
        val after = classifier.classify(record(), failure(NormalizedProviderFailureType.BILLING_REQUIRED))

        assertEquals(ProviderAvailabilityState.BILLING_REQUIRED, after.state)
        assertNull(after.cooldownUntilEpochMs)
        assertNull(after.resetAtEpochMs)
        assertTrue(after.paidLocked)
        assertFalse(after.availableAt(now + 100_000_000_000L), "no elapsed time may clear this")
        assertTrue(ProviderAvailabilityState.BILLING_REQUIRED.neverAutoRetries)
    }

    @Test
    fun `an auth failure is skipped until the key changes`() {
        val after = classifier.classify(record(), failure(NormalizedProviderFailureType.AUTH_FAILED))

        assertEquals(ProviderAvailabilityState.AUTH_FAILED, after.state)
        assertFalse(after.availableAt(now + 100_000_000_000L))
        assertTrue(ProviderAvailabilityState.AUTH_FAILED.needsHuman)
    }

    /**
     * Not an outage. Collapsing this into OFFLINE would drop a healthy provider
     * out of every chain it appears in over one withdrawn model name.
     */
    @Test
    fun `a missing model is its own state, distinct from an outage`() {
        val after = classifier.classify(record(), failure(NormalizedProviderFailureType.MODEL_MISSING))

        assertEquals(ProviderAvailabilityState.MODEL_MISSING, after.state)
        assertFalse(after.state.needsHuman, "an alternate model may work without a human")
    }

    @Test
    fun `a timeout takes a short cooldown, not a long one`() {
        val after = classifier.classify(record(), failure(NormalizedProviderFailureType.TIMEOUT))

        assertEquals(ProviderAvailabilityState.COOLDOWN, after.state)
        assertEquals(now + 30_000, after.cooldownUntilEpochMs)
        assertTrue(after.availableAt(now + 31_000))
    }

    /**
     * One unparseable reply does not mean the next will be. Marking it unusable
     * would drop a healthy provider on a single bad response.
     */
    @Test
    fun `a malformed response degrades rather than disables`() {
        val after = classifier.classify(record(), failure(NormalizedProviderFailureType.MALFORMED_RESPONSE))

        assertEquals(ProviderAvailabilityState.DEGRADED, after.state)
        assertTrue(after.availableAt(now), "degraded is still usable")
    }

    @Test
    fun `a cancellation is not a provider fault and does not touch its health`() {
        val before = record()

        val after = classifier.classify(before, failure(NormalizedProviderFailureType.CANCELLED))

        assertEquals(before.state, after.state)
        assertEquals(before.successScore, after.successScore, "the operator stopping it says nothing about the provider")
    }

    @Test
    fun `each failure decays the success score and a success lifts it`() {
        val failed = classifier.classify(record(), failure(NormalizedProviderFailureType.TIMEOUT))
        assertTrue(failed.successScore < 1.0)

        val recovered = classifier.succeed(failed, latencyMillis = 200)
        assertTrue(recovered.successScore > failed.successScore)
        assertEquals(ProviderAvailabilityState.READY, recovered.state)
        assertNull(recovered.cooldownUntilEpochMs)
    }

    @Test
    fun `latency is blended so one slow call does not redefine the estimate`() {
        val warm = classifier.succeed(record().copy(latencyMsAvg = 100), latencyMillis = 100)
        val spiked = classifier.succeed(warm, latencyMillis = 5_000)

        assertTrue(spiked.latencyMsAvg!! < 2_000, "a single spike must not dominate")
        assertTrue(spiked.latencyMsAvg!! > 100)
    }

    // -- preference order -----------------------------------------------------

    private fun candidate(id: String, weight: Int, score: Double = 1.0, latency: Long = 100) =
        ProviderEligibility(
            provider = ProviderDescriptor(id, id, CostMode.FREE, weight, setOf(ApiCapability.CHAT)),
            quota = ProviderQuotaRecord(id, CostMode.FREE, weight, configured = true, verified = true,
                state = ProviderAvailabilityState.READY, successScore = score, latencyMsAvg = latency),
            eligible = true,
            reason = "eligible"
        )

    /**
     * The free-first guarantee expressed as an ordering. A weighted score would
     * let a fast paid provider beat a slow free one; lexicographic comparison
     * cannot.
     */
    @Test
    fun `quota weight dominates every other term`() {
        val fastPaid = candidate("openai", weight = 9, score = 1.0, latency = 10)
        val slowFree = candidate("groq", weight = 1, score = 0.3, latency = 5_000)

        val ordered = ProviderPreferenceOrder.order(listOf(fastPaid, slowFree))

        assertEquals("groq", ordered.first().provider.id, "cost must outrank speed and reliability")
    }

    @Test
    fun `chain position breaks ties within a weight tier`() {
        val gemini = candidate("gemini", weight = 1)
        val groq = candidate("groq", weight = 1)

        val ordered = ProviderPreferenceOrder.orderForChain(listOf(gemini, groq), FallbackChain.CHAT)

        assertEquals("groq", ordered.first().provider.id, "groq leads CHAT_CHAIN")
    }

    @Test
    fun `success score breaks ties when weight and position agree`() {
        val reliable = candidate("a", weight = 1, score = 0.95)
        val flaky = candidate("b", weight = 1, score = 0.20)

        assertEquals("a", ProviderPreferenceOrder.order(listOf(flaky, reliable)).first().provider.id)
    }

    @Test
    fun `latency breaks ties when weight, position and score agree`() {
        val fast = candidate("a", weight = 1, score = 1.0, latency = 50)
        val slow = candidate("b", weight = 1, score = 1.0, latency = 900)

        assertEquals("a", ProviderPreferenceOrder.order(listOf(slow, fast)).first().provider.id)
    }

    @Test
    fun `a provider outside the chain sorts last rather than first`() {
        val inChain = candidate("groq", weight = 1)
        val outside = candidate("nvidia", weight = 1)

        val ordered = ProviderPreferenceOrder.orderForChain(listOf(outside, inChain), FallbackChain.CHAT)

        assertEquals("groq", ordered.first().provider.id)
    }

    @Test
    fun `an unknown remaining quota is not treated as exhausted`() {
        val unreported = candidate("a", weight = 1).copy(quota = null)
        val healthy = candidate("b", weight = 2)

        val ordered = ProviderPreferenceOrder.order(listOf(healthy, unreported))

        assertEquals("a", ordered.first().provider.id, "silence about quota is not the same as having none")
    }

    @Test
    fun `cooldown risk rises with a worse state and a worse score`() {
        val healthy = candidate("a", weight = 1, score = 1.0)
        val shaky = candidate("b", weight = 1, score = 0.1).let {
            it.copy(quota = it.quota!!.copy(state = ProviderAvailabilityState.COOLDOWN))
        }

        assertTrue(ProviderPreferenceOrder.cooldownRisk(shaky) > ProviderPreferenceOrder.cooldownRisk(healthy))
    }

    @Test
    fun `the order explains itself with the terms that decided it`() {
        val explained = ProviderPreferenceOrder.explain(
            ProviderPreferenceOrder.order(listOf(candidate("groq", 1), candidate("openai", 9)))
        )

        assertTrue(explained.startsWith("groq(w=1"))
        assertTrue(explained.contains(" > openai(w=9"))
    }
}
