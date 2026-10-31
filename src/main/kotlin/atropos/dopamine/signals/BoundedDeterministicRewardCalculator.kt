/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dopamine.signals

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * BoundedDeterministicRewardCalculator
 *
 * Implements Phase 20 & 21 Closed-Form Deterministic Multi-Factor Reward Calculus.
 * Strictly bounded via tanh-clamped sensitization to prevent gradient explosions in deep RL:
 * R(e) = B(e) * C(e) * H(e) * M(e) * D(e) + P(e) - L(e)
 */
data class RewardFactorVector(
    val baseOutcome: Double,
    val connectivityDensity: Double,
    val habituationMultiplier: Double,
    val myelinationScale: Double,
    val developmentalGain: Double,
    val painPenalty: Double,
    val lengthPenalty: Double,
    val compositeScalarReward: Double
)

class BoundedDeterministicRewardCalculator(
    private val etaDecay: Double = 0.08,
    private val lengthPenaltyAlpha: Double = 0.0001
) {
    private val actionFrequencies = ConcurrentHashMap<String, AtomicInteger>()
    private val faultRecurrences = ConcurrentHashMap<String, AtomicInteger>()
    private val myelinationCounters = ConcurrentHashMap<String, AtomicInteger>()

    fun evaluateReward(
        actionKey: String,
        isPermanentFix: Boolean,
        isRejectedOrSurvivingMutant: Boolean,
        isFraudOrDomainViolation: Boolean,
        connectedEdgesCount: Int,
        averageDegree: Double = 4.0,
        developmentalPhaseGain: Double = 1.0,
        trajectoryTokenLength: Int = 100
    ): RewardFactorVector {
        // 1. Base outcome term B(e)
        val baseOutcome = when {
            isFraudOrDomainViolation -> -10.0
            isPermanentFix -> +1.0
            isRejectedOrSurvivingMutant -> -1.5
            else -> 0.1
        }

        // 2. Connectivity Density term C(e) = tanh(alpha * |E| / max(1, deg_avg))
        val normConn = connectedEdgesCount.toDouble() / Math.max(1.0, averageDegree)
        val connectivityDensity = Math.tanh(0.75 * normConn)

        // 3. Habituation Term H(e) = exp(-eta * N_identical)
        val nIdentical = actionFrequencies.computeIfAbsent(actionKey) { AtomicInteger(0) }.incrementAndGet()
        val habituation = Math.exp(-etaDecay * (nIdentical - 1))

        // 4. Myelination Term M(e) = 1.0 + 0.2 * log(1 + myelinationFactor)
        val pathwaySuccess = if (isPermanentFix) {
            myelinationCounters.computeIfAbsent(actionKey) { AtomicInteger(0) }.incrementAndGet()
        } else {
            myelinationCounters.computeIfAbsent(actionKey) { AtomicInteger(0) }.get()
        }
        val myelination = 1.0 + 0.2 * Math.log(1.0 + pathwaySuccess)

        // 5. Developmental Phase Gate D(e)
        val developmentalGain = developmentalPhaseGain.coerceIn(0.1, 2.0)

        // 6. Pain / Sensitization Term P(e) (Tanh-clamped to prevent gradient blowup)
        val pain = if (!isPermanentFix && isRejectedOrSurvivingMutant) {
            val recurrences = faultRecurrences.computeIfAbsent(actionKey) { AtomicInteger(0) }.incrementAndGet()
            -3.0 * Math.tanh(0.3 * recurrences)
        } else {
            0.0
        }

        // 7. Length Penalty L(e) combats verbosity hacking
        val lengthPenalty = (trajectoryTokenLength * lengthPenaltyAlpha).coerceAtMost(0.5)

        // Composite scalar reward calculation
        val composite = (baseOutcome * connectivityDensity * habituation * myelination * developmentalGain) + pain - lengthPenalty

        return RewardFactorVector(
            baseOutcome = baseOutcome,
            connectivityDensity = connectivityDensity,
            habituationMultiplier = habituation,
            myelinationScale = myelination,
            developmentalGain = developmentalGain,
            painPenalty = pain,
            lengthPenalty = lengthPenalty,
            compositeScalarReward = composite
        )
    }

    companion object {
        val defaultCalculator = BoundedDeterministicRewardCalculator()
    }
}
