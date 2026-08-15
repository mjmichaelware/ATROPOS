// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.provider

data class EligibilityScore(
    val providerId: String,
    val remainingEstimate: Int,
    val recentSuccessScore: Double,
    val latencyEstimate: Long,
    val cooldownRisk: Double,
    val modelMissing: Boolean
) : Comparable<EligibilityScore> {
    override fun compareTo(other: EligibilityScore): Int {
        // Exclude missing model first
        if (this.modelMissing && !other.modelMissing) return 1
        if (!this.modelMissing && other.modelMissing) return -1
        
        // Prefer lower cooldown risk
        val cooldownDiff = this.cooldownRisk.compareTo(other.cooldownRisk)
        if (cooldownDiff != 0) return cooldownDiff

        // Prefer higher success score
        val successDiff = other.recentSuccessScore.compareTo(this.recentSuccessScore)
        if (successDiff != 0) return successDiff

        // Prefer higher remaining estimate
        val remainingDiff = other.remainingEstimate.compareTo(this.remainingEstimate)
        if (remainingDiff != 0) return remainingDiff

        // Prefer lower latency estimate
        return this.latencyEstimate.compareTo(other.latencyEstimate)
    }
}

object EligibilityAlgorithm {
    fun score(health: ProviderHealth): EligibilityScore {
        val remaining = when (health.state) {
            ProviderAvailabilityState.READY -> 1000
            ProviderAvailabilityState.DEGRADED -> 500
            ProviderAvailabilityState.COOLDOWN -> 100
            else -> 0
        }
        val risk = when (health.state) {
            ProviderAvailabilityState.COOLDOWN -> 1.0
            ProviderAvailabilityState.DEGRADED -> 0.5
            else -> 0.0
        }
        return EligibilityScore(
            providerId = health.providerId,
            remainingEstimate = remaining,
            recentSuccessScore = health.successScore,
            latencyEstimate = health.latencyMsAvg ?: 1000L,
            cooldownRisk = risk,
            modelMissing = health.activeModel == null
        )
    }

    fun rank(healths: List<ProviderHealth>): List<EligibilityScore> {
        return healths.map { score(it) }.sorted()
    }
}
