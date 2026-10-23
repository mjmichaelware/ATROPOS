/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.dopamine.NeuralPathwayRewardEngine

/**
 * SD7-T1-TEL11: Mutation Testing Oracle Telemetry.
 *
 * Implements functioning mathematical evaluation of test suite rigor:
 * Calculates exact mutation kill ratio: R_kill = K / M.
 * Applies continuous mathematical penalty for R_kill < 0.85 (vacuous assertion defect)
 * and scaled reward for R_kill >= 0.85.
 */
class ToigMutationTestingOracleTelemetry(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    companion object {
        const val KILL_RATIO_PASS_THRESHOLD: Double = 0.85
        const val BASE_VACUOUS_PENALTY: Double = -1.5
        const val BASE_KILL_REWARD: Double = 2.0
        const val MUTANT_TIMEOUT_MS: Long = 2000L
    }

    data class MutationEvaluationResult(
        val totalMutants: Int,
        val killedMutants: Int,
        val survivingMutants: Int,
        val timedOutMutants: Int,
        val killRatio: Double,
        val evaluatedScore: Double,
        val passedThreshold: Boolean
    )

    /**
     * Functional calculation of mutation score based on kill ratio.
     */
    fun calculateMutationScore(killed: Int, total: Int): Double {
        if (total <= 0) return BASE_VACUOUS_PENALTY
        val ratio = (killed.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
        return if (ratio < KILL_RATIO_PASS_THRESHOLD) {
            // Scaled penalty proportional to defect severity: -1.5 * (1 + (0.85 - R) / 0.85)
            val severityMultiplier = 1.0 + ((KILL_RATIO_PASS_THRESHOLD - ratio) / KILL_RATIO_PASS_THRESHOLD)
            BASE_VACUOUS_PENALTY * severityMultiplier
        } else {
            // Scaled reward: +2.0 * (1 + (R - 0.85) / 0.15 * 0.25)
            val bonusFraction = (ratio - KILL_RATIO_PASS_THRESHOLD) / (1.0 - KILL_RATIO_PASS_THRESHOLD)
            BASE_KILL_REWARD * (1.0 + bonusFraction * 0.25)
        }
    }

    fun recordMutationRun(
        instanceId: String,
        totalMutants: Int,
        killedMutants: Int,
        survivingMutants: Int,
        timedOutMutants: Int = 0
    ): MutationEvaluationResult {
        val total = totalMutants.coerceAtLeast(1)
        val killed = killedMutants.coerceIn(0, total)
        val surviving = survivingMutants.coerceIn(0, total - killed)
        val ratio = killed.toDouble() / total.toDouble()
        val score = calculateMutationScore(killed, total)
        val passed = ratio >= KILL_RATIO_PASS_THRESHOLD

        val payload = MutationKillRatioRecord(
            totalMutants = total,
            killedMutants = killed,
            killRatio = ratio,
            vacuousAssertionDefect = !passed,
            survivingMutantIds = (1..surviving).map { "mutant-$it" }
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "mut-$instanceId-$total-$killed",
            payload = payload,
            rewardSignal = score
        )
        ringBuffer?.offer(record)

        return MutationEvaluationResult(
            totalMutants = total,
            killedMutants = killed,
            survivingMutants = surviving,
            timedOutMutants = timedOutMutants,
            killRatio = ratio,
            evaluatedScore = score,
            passedThreshold = passed
        )
    }
}
