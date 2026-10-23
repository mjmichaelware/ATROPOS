/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.dopamine.AutomaticDopamineExecutionListener
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.max

/**
 * SD7-T1-TEL05: Reasoning Trace Process Reward Logger.
 *
 * Implements fine-grained process-supervision step-level scoring (PEARL / AgentPRM / ACE)
 * using deterministic convex weighting and hash-linked DAG divergence backtracking.
 */
class ReasoningTraceProcessRewardLogger(
    private val ringBuffer: TelemetryRingBuffer? = null,
    private val dopamineListener: AutomaticDopamineExecutionListener? = null
) {
    /**
     * Convex weights: w_confidence + w_tokens + w_backtrack + w_rejection == 1.0
     */
    data class ConvexWeights(
        val wConfidence: Double = 0.40,
        val wTokenEfficiency: Double = 0.25,
        val wBacktrackStability: Double = 0.20,
        val wHypothesisQuality: Double = 0.15
    ) {
        init {
            val sum = wConfidence + wTokenEfficiency + wBacktrackStability + wHypothesisQuality
            require(kotlin.math.abs(sum - 1.0) < 1e-4) { "Weights must form a valid convex combination summing to 1.0" }
        }
    }

    data class StepSnapshot(
        val stepIndex: Int,
        val thoughtSnippet: String,
        val premiseTokens: Int,
        val deductionTokens: Int,
        val confidence: Double,
        val backtracks: Int,
        val rejectedHypothesesCount: Int,
        val processScore: Double,
        val previousStepHash: String,
        val stepHash: String
    )

    private val weights = ConvexWeights()
    private val activeTraces = mutableMapOf<String, MutableList<StepSnapshot>>()

    /**
     * Functional computation of intermediate process score via convex combination.
     */
    fun computeConvexProcessScore(
        confidence: Double,
        premiseTokens: Int,
        deductionTokens: Int,
        backtracks: Int,
        rejectedCount: Int
    ): Double {
        val c = confidence.coerceIn(0.0, 1.0)
        val totalTokens = (premiseTokens + deductionTokens).coerceAtLeast(1)
        val tokenEfficiency = (deductionTokens.toDouble() / totalTokens).coerceIn(0.0, 1.0)
        val backtrackStability = (1.0 / (1.0 + backtracks.coerceAtLeast(0))).coerceIn(0.0, 1.0)
        val hypothesisPenalty = (1.0 - exp(-0.5 * rejectedCount.coerceAtLeast(0))).coerceIn(0.0, 1.0)

        // Convex combination in range [-0.5, +0.5]
        val convexSum = (weights.wConfidence * c) +
                (weights.wTokenEfficiency * tokenEfficiency) +
                (weights.wBacktrackStability * backtrackStability) -
                (weights.wHypothesisQuality * hypothesisPenalty)

        return convexSum - 0.5
    }

    /**
     * Records an intermediate thought milestone with deterministic convex process scoring.
     */
    fun recordThoughtStep(
        traceId: String,
        stepIndex: Int,
        thoughtSnippet: String,
        premiseTokens: Int,
        deductionTokens: Int,
        confidence: Double,
        backtracks: Int = 0,
        rejectedHypotheses: List<String> = emptyList()
    ): StepSnapshot {
        val history = activeTraces.getOrPut(traceId) { mutableListOf() }
        val prevHash = history.lastOrNull()?.stepHash ?: "0".repeat(64)
        val stepHash = sha256Hex("$prevHash-$stepIndex-$thoughtSnippet-$confidence-$backtracks")

        val processScore = computeConvexProcessScore(
            confidence = confidence,
            premiseTokens = premiseTokens,
            deductionTokens = deductionTokens,
            backtracks = backtracks,
            rejectedCount = rejectedHypotheses.size
        )

        val snapshot = StepSnapshot(
            stepIndex = stepIndex,
            thoughtSnippet = thoughtSnippet.take(500),
            premiseTokens = premiseTokens,
            deductionTokens = deductionTokens,
            confidence = confidence.coerceIn(0.0, 1.0),
            backtracks = backtracks,
            rejectedHypothesesCount = rejectedHypotheses.size,
            processScore = processScore,
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
            backtracks = backtracks,
            rejectedHypotheses = rejectedHypotheses,
            stepHash = stepHash
        )

        val record = FrontierSweTelemetryRecord.create(
            instanceId = traceId,
            h0PromptLineage = prevHash,
            payload = payload,
            rewardSignal = processScore
        )
        ringBuffer?.offer(record)
        return snapshot
    }

    /**
     * Backtracks to identify and penalize the exact divergence point using confidence loss gradients.
     */
    fun onExecutionOutcome(traceId: String, passed: Boolean, failingStepIndex: Int? = null) {
        val steps = activeTraces.remove(traceId) ?: return
        if (!passed && steps.isNotEmpty()) {
            val inflectionIndex = failingStepIndex ?: findDivergenceInflection(steps)
            val divergentStep = steps.getOrNull(inflectionIndex) ?: steps.last()

            // Calculate gradient loss: difference between peak confidence and divergent confidence
            val peakConfidence = steps.maxOfOrNull { it.confidence } ?: 1.0
            val confidenceDrop = max(0.0, peakConfidence - divergentStep.confidence)
            val divergencePenalty = -0.5 * (1.0 + confidenceDrop)

            val divergencePayload = ThoughtTraceProcessGraph(
                traceId = traceId,
                stepIndex = divergentStep.stepIndex,
                premiseTokens = divergentStep.premiseTokens,
                deductionTokens = divergentStep.deductionTokens,
                confidence = divergentStep.confidence,
                backtracks = divergentStep.backtracks + 1,
                rejectedHypotheses = listOf("process_divergence_inflection"),
                stepHash = divergentStep.stepHash
            )

            val penaltyRecord = FrontierSweTelemetryRecord.create(
                instanceId = traceId,
                h0PromptLineage = divergentStep.previousStepHash,
                payload = divergencePayload,
                rewardSignal = divergencePenalty
            )
            ringBuffer?.offer(penaltyRecord)
        }
    }

    private fun findDivergenceInflection(steps: List<StepSnapshot>): Int {
        if (steps.size <= 1) return 0
        var worstGradient = 0.0
        var worstIndex = steps.size - 1

        for (i in 1 until steps.size) {
            val delta = steps[i].confidence - steps[i - 1].confidence
            if (delta < worstGradient) {
                worstGradient = delta
                worstIndex = i
            }
        }
        return worstIndex
    }

    private fun sha256Hex(str: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
