/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.verification.MutationReport
import atropos.core.verification.MutationTestingOracle

/**
 * SD7-T1-TEL11: Mutation Testing Oracle Telemetry Bridge.
 *
 * Evaluates test suite rigor by tracking mutant survival.
 * Kill ratio < 0.85 triggers PENALTY -1.5 (vacuous_assertion_defect).
 * Kill ratio >= 0.85 triggers REWARD +2.0.
 */
class ToigMutationTestingOracleTelemetry(
    private val rewardStore: RewardPenaltyStore = RewardPenaltyStore(),
    private val ringBuffer: TelemetryRingBuffer? = null,
    private val thresholdKillRatio: Double = 0.85
) {
    fun recordMutationEvaluation(
        agentId: String,
        instanceId: String,
        report: MutationReport
    ): Double {
        val killRatio = report.killRatio
        val isVacuous = killRatio < thresholdKillRatio
        val signal = if (isVacuous) -1.5 else 2.0

        if (isVacuous) {
            rewardStore.recordPenalty(
                agentId = agentId,
                action = "mutation.vacuous_assertion_defect",
                value = signal,
                reason = "Mutation kill ratio $killRatio below 0.85 threshold (${report.killedMutants}/${report.totalMutants})"
            )
        } else {
            rewardStore.recordReward(
                agentId = agentId,
                action = "mutation.rigorous_test_suite",
                value = signal,
                reason = "Mutation kill ratio $killRatio meets rigor threshold (${report.killedMutants}/${report.totalMutants})"
            )
        }

        val payload = MutationKillRatioRecord(
            totalMutants = report.totalMutants,
            killedMutants = report.killedMutants,
            killRatio = killRatio,
            vacuousAssertionDefect = isVacuous,
            survivingMutantIds = report.survivedMutants
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "mutation-eval-$instanceId",
            payload = payload,
            rewardSignal = signal
        )
        ringBuffer?.offer(record)

        return signal
    }
}
