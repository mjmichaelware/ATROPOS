/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.autonomy.RewardPenaltyStore

/**
 * SD7-T1-TEL10: Static Analysis & Linter Defect Harvester.
 *
 * Transforms linter violations, code smells, and static security defects from
 * detekt, ruff, clippy, go vet, and eslint into graded process penalties:
 * - Style/formatting: -0.2
 * - Type/nullability smell: -0.5
 * - Security/memory/unsafe defect: -1.0
 */
class StaticAnalysisAndLinterDefectHarvester(
    private val rewardStore: RewardPenaltyStore = RewardPenaltyStore(),
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    fun recordLinterDefect(
        agentId: String,
        instanceId: String,
        linterName: String,
        ruleId: String,
        severity: String,
        complexityMetric: Int = 0,
        isUnsafeSecurityDefect: Boolean = false
    ): Double {
        val penalty = when {
            isUnsafeSecurityDefect || severity.equals("error", ignoreCase = true) -> -1.0
            severity.equals("warning", ignoreCase = true) -> -0.5
            else -> -0.2
        }

        rewardStore.recordPenalty(
            agentId = agentId,
            action = "static_analysis.$linterName.$ruleId",
            value = penalty,
            reason = "Static defect detected by $linterName: $ruleId ($severity)"
        )

        val payload = StaticAnalysisDefectRecord(
            linter = linterName,
            ruleId = ruleId,
            severity = severity,
            complexityMetrics = complexityMetric,
            unsafeFlag = isUnsafeSecurityDefect,
            penalty = penalty
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "linter-$ruleId",
            payload = payload,
            rewardSignal = penalty
        )
        ringBuffer?.offer(record)

        return penalty
    }
}
