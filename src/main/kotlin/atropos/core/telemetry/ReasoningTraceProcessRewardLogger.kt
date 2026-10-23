/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.dopamine.AutomaticDopamineExecutionListener
import java.security.MessageDigest

/**
 * SD7-T1-TEL05: Reasoning Trace Process Reward Logger.
 *
 * Implements fine-grained process-supervision (PEARL / AgentPRM / ACE).
 * Scores intermediate reasoning milestones independently from final program verification.
 * Backtracks on verification failure to penalize the earliest divergence step.
 */
class ReasoningTraceProcessRewardLogger(
    private val ringBuffer: TelemetryRingBuffer? = null,
    private val dopamineListener: AutomaticDopamineExecutionListener? = null
) {
    data class StepSnapshot(
        val stepIndex: Int,
        val thoughtSnippet: String,
        val premiseTokens: Int,
        val deductionTokens: Int,
        val confidence: Double,
        val previousStepHash: String,
        val stepHash: String
    )

    private val activeTraces = mutableMapOf<String, MutableList<StepSnapshot>>()

    /**
     * Records an intermediate thought milestone.
     */
    fun recordThoughtStep(
        traceId: String,
        stepIndex: Int,
        thoughtSnippet: String,
        premiseTokens: Int,
        deductionTokens: Int,
        confidence: Double,
        rejectedHypotheses: List<String> = emptyList()
    ): StepSnapshot {
        val history = activeTraces.getOrPut(traceId) { mutableListOf() }
        val prevHash = history.lastOrNull()?.stepHash ?: "0".repeat(64)
        val stepHash = sha256Hex("$prevHash-$stepIndex-$thoughtSnippet-$confidence")

        val snapshot = StepSnapshot(
            stepIndex = stepIndex,
            thoughtSnippet = thoughtSnippet.take(500),
            premiseTokens = premiseTokens,
            deductionTokens = deductionTokens,
            confidence = confidence.coerceIn(0.0, 1.0),
            previousStepHash = prevHash,
            stepHash = stepHash
        )
        history.add(snapshot)

        val payload = ThoughtTraceProcessGraph(
            traceId = traceId,
            stepIndex = stepIndex,
            premiseTokens = premiseTokens,
            deductionTokens = deductionTokens,
            confidence = confidence,
            backtracks = 0,
            rejectedHypotheses = rejectedHypotheses,
            stepHash = stepHash
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = traceId,
            h0PromptLineage = prevHash,
            payload = payload,
            rewardSignal = (confidence - 0.5) * 0.2 // intermediate milestone micro-reward
        )
        ringBuffer?.offer(record)
        return snapshot
    }

    /**
     * Backtracks to flag the step where reasoning diverged on execution failure.
     */
    fun onExecutionOutcome(traceId: String, passed: Boolean, failingStepIndex: Int? = null) {
        val steps = activeTraces.remove(traceId) ?: return
        if (!passed && steps.isNotEmpty()) {
            val penalizedIndex = failingStepIndex ?: (steps.size - 1)
            val step = steps.getOrNull(penalizedIndex) ?: steps.last()

            val divergencePayload = ThoughtTraceProcessGraph(
                traceId = traceId,
                stepIndex = step.stepIndex,
                premiseTokens = step.premiseTokens,
                deductionTokens = step.deductionTokens,
                confidence = step.confidence,
                backtracks = 1,
                rejectedHypotheses = listOf("divergence_detected"),
                stepHash = step.stepHash
            )

            val penaltyRecord = FrontierSweTelemetryRecord.create(
                instanceId = traceId,
                h0PromptLineage = step.previousStepHash,
                payload = divergencePayload,
                rewardSignal = -0.5 // process-supervision step divergence penalty
            )
            ringBuffer?.offer(penaltyRecord)
        }
    }

    private fun sha256Hex(str: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
