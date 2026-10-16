/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.time.Instant

data class VerificationEvent(
    val timestamp: Instant,
    val predicateId: String,
    val verified: Boolean
)

object AcceptanceVelocity {
    fun calculate(events: List<VerificationEvent>, windowHours: Long = 24): Double {
        if (events.isEmpty()) return 0.0
        val now = Instant.now()
        val limit = now.minusSeconds(windowHours * 3600)
        val filtered = events.filter { it.timestamp.isAfter(limit) && it.verified }
        if (filtered.isEmpty()) return 0.0
        
        val distinctPredicates = filtered.map { it.predicateId }.distinct().size
        val days = windowHours / 24.0
        return distinctPredicates / days
    }

    /**
     * SD7-R-092: Sabermetrics-style rate statistics.
     * FactoryWAR = (ΔVerifiedPredicates - expected) / cost-normalized replacement level.
     */
    fun calculateFactoryWar(
        events: List<VerificationEvent>,
        expectedPredicates: Double,
        replacementLevelCost: Double = 1.0,
        actualCost: Double = 1.0,
        windowHours: Long = 24
    ): Double {
        if (events.isEmpty()) return 0.0
        val deltaVerified = calculate(events, windowHours)
        val normalizedReplacement = if (actualCost > 0.0) replacementLevelCost * actualCost else replacementLevelCost.coerceAtLeast(1e-6)
        return (deltaVerified - expectedPredicates) / normalizedReplacement
    }
}
