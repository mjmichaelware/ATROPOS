/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.dopamine.NeuralPathwayRewardEngine

/**
 * SD7-T1-TEL04: Human Preference Feedback Collector.
 *
 * Captures implicit human developer feedback: dwell time, diff hunk approvals/rejections,
 * cursor edits within 300 s, and interactive cancellation prompts.
 * Enforces: approved hunk -> +1.0, rejected/modified hunk -> -1.0.
 * Capped dwell time at 30,000 ms.
 */
class HumanPreferenceFeedbackCollector(
    private val rewardStore: RewardPenaltyStore = RewardPenaltyStore(),
    private val ringBuffer: TelemetryRingBuffer? = null,
    private val maxDwellTimeMs: Long = 30000L
) {
    /**
     * Records a developer's acceptance or modification of an agent-generated diff hunk.
     */
    fun recordHunkFeedback(
        agentId: String,
        instanceId: String,
        diffHunk: String,
        approved: Boolean,
        dwellDurationMs: Long,
        tokenDelta: Int = 0
    ) {
        val clampedDwell = dwellDurationMs.coerceIn(0L, maxDwellTimeMs)
        val score = if (approved) {
            NeuralPathwayRewardEngine.BASE_APPROVED_HUNK
        } else {
            NeuralPathwayRewardEngine.BASE_REJECTED_HUNK
        }

        // 1. Record into Sovereign Reward Store
        if (approved) {
            rewardStore.recordReward(
                agentId = agentId,
                action = "human_preference.hunk_approved",
                value = score,
                reason = "hunk accepted after ${clampedDwell}ms dwell"
            )
        } else {
            rewardStore.recordPenalty(
                agentId = agentId,
                action = "human_preference.hunk_rejected",
                value = score,
                reason = "hunk rejected/modified with tokenDelta=$tokenDelta"
            )
        }

        // 2. Stream to telemetry ring buffer
        val payload = HumanPreferenceDelta(
            dwellTimeMs = clampedDwell,
            approved = approved,
            diffHunk = diffHunk.take(2000),
            tokenDelta = tokenDelta
        )
        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "prompt-$instanceId",
            payload = payload,
            rewardSignal = score
        )
        ringBuffer?.offer(record)
    }
}
