/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.autonomy.RewardPenaltyStore
import kotlin.math.min

/**
 * SD7-T1-TEL10: Static Analysis & Linter Defect Harvester.
 *
 * Transforms linter violations, code smells, and static security defects from
 * detekt, ruff, clippy, go vet, and eslint into graded process penalties:
 * - Style/formatting: -0.2
 * - Type/nullability smell: -0.5
 * - Security/memory/unsafe defect: -1.0
 * Calculates scaled cyclomatic complexity penalties.
 */
class StaticAnalysisAndLinterDefectHarvester(
    private val rewardStore: RewardPenaltyStore = RewardPenaltyStore(),
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    companion object {
        const val PENALTY_STYLE = -0.2
        const val PENALTY_TYPE_SMELL = -0.5
        const val PENALTY_SECURITY_UNSAFE = -1.0
        const val COMPLEXITY_THRESHOLD = 15
    }

    /**
     * Functional calculation of penalty based on category and cyclomatic complexity.
     */
    fun calculateDefectPenalty(
        severity: String,
        isUnsafeSecurityDefect: Boolean,
        complexityMetric: Int
    ): Double {
        var basePenalty = when {
            isUnsafeSecurityDefect || severity.equals("error", ignoreCase = true) -> PENALTY_SECURITY_UNSAFE
            severity.equals("warning", ignoreCase = true) -> PENALTY_TYPE_SMELL
            else -> PENALTY_STYLE
        }

        // Additional complexity penalty scaling: -0.05 per unit above threshold capped at -0.5
        if (complexityMetric > COMPLEXITY_THRESHOLD) {
            val excess = complexityMetric - COMPLEXITY_THRESHOLD
            val complexityPenalty = min(0.5, excess * 0.05)
            basePenalty -= complexityPenalty
        }

        return basePenalty
    }

    fun recordLinterDefect(
        agentId: String,
        instanceId: String,
        linterName: String,
        ruleId: String,
        severity: String,
        complexityMetric: Int = 0,
        isUnsafeSecurityDefect: Boolean = false
    ): Double {
        val penalty = calculateDefectPenalty(severity, isUnsafeSecurityDefect, complexityMetric)

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
