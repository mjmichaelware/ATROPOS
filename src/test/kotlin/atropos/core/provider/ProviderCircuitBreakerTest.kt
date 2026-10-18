/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderCircuitBreakerTest {

    @BeforeTest
    fun setUp() {
        ProviderCircuitBreaker.reset()
    }

    @Test
    fun trips_into_quarantined_on_http_402_or_401() {
        assertFalse(ProviderCircuitBreaker.isQuarantined("deepseek"))
        assertEquals(ProviderHealthState.HEALTHY, ProviderCircuitBreaker.stateOf("deepseek"))

        ProviderCircuitBreaker.recordFailure("deepseek", "quota exhausted", statusCode = 402)

        assertTrue(ProviderCircuitBreaker.isQuarantined("deepseek"))
        assertEquals(ProviderHealthState.QUARANTINED, ProviderCircuitBreaker.stateOf("deepseek"))
    }

    @Test
    fun trips_into_quarantined_on_http_429_with_timed_backoff() {
        assertFalse(ProviderCircuitBreaker.isQuarantined("groq"))

        ProviderCircuitBreaker.quarantineUntil("groq", durationSeconds = 60, reason = "Rate limited")

        assertTrue(ProviderCircuitBreaker.isQuarantined("groq"))
        assertTrue(ProviderCircuitBreaker.quarantineReason("groq")!!.contains("backoff"))
    }

    @Test
    fun transitions_to_exhausted_after_three_strikes() {
        val provider = "cerebras"
        assertFalse(ProviderCircuitBreaker.isQuarantined(provider))

        ProviderCircuitBreaker.recordFailure(provider, "attempt 1 failed")
        assertFalse(ProviderCircuitBreaker.isQuarantined(provider))

        ProviderCircuitBreaker.recordFailure(provider, "attempt 2 failed")
        assertFalse(ProviderCircuitBreaker.isQuarantined(provider))

        ProviderCircuitBreaker.recordFailure(provider, "attempt 3 failed")
        assertTrue(ProviderCircuitBreaker.isQuarantined(provider))
        assertEquals(ProviderHealthState.EXHAUSTED, ProviderCircuitBreaker.stateOf(provider))
    }

    @Test
    fun success_restores_healthy_state() {
        val provider = "sambanova"
        ProviderCircuitBreaker.quarantine(provider, "temporary glitch")
        assertTrue(ProviderCircuitBreaker.isQuarantined(provider))

        ProviderCircuitBreaker.recordSuccess(provider)
        assertFalse(ProviderCircuitBreaker.isQuarantined(provider))
        assertEquals(ProviderHealthState.HEALTHY, ProviderCircuitBreaker.stateOf(provider))
    }
}
