// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EligibilityAlgorithmTest {

    @Test
    fun `scores ready provider highly`() {
        val health = ProviderHealth(
            providerId = "p1",
            state = ProviderAvailabilityState.READY,
            activeModel = "gpt-4",
            latencyMsAvg = 200L,
            successScore = 0.99
        )
        val score = EligibilityAlgorithm.score(health)
        assertEquals("p1", score.providerId)
        assertEquals(1000, score.remainingEstimate)
        assertEquals(0.99, score.recentSuccessScore)
        assertEquals(200L, score.latencyEstimate)
        assertEquals(0.0, score.cooldownRisk)
        assertEquals(false, score.modelMissing)
    }

    @Test
    fun `ranks ready provider before degraded`() {
        val ready = ProviderHealth(
            providerId = "ready",
            state = ProviderAvailabilityState.READY,
            activeModel = "gpt-4",
            successScore = 0.9
        )
        val degraded = ProviderHealth(
            providerId = "degraded",
            state = ProviderAvailabilityState.DEGRADED,
            activeModel = "gpt-4",
            successScore = 0.9
        )
        val ranked = EligibilityAlgorithm.rank(listOf(degraded, ready))
        assertEquals("ready", ranked.first().providerId)
    }
}
