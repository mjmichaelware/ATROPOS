// AUTO-GENERATED TIER-1 ATOM: DC-DOP-013 - CumulativeDiscountedReturn
// HUD: #64D2FF · REWARD: gamma-discounted sum
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-013: CumulativeDiscountedReturn
 * Category: Dopamine / Reward
 * Expanded Context: gamma-discounted sum of all base + escalated + amplified signals across the entire trajectory.
 * Research: Research trajectory-level return for process supervision.
 * Implementation: Emit cumulative discounted return vector paired with terminal verification status.
 * Wire / Core Producers: RewardPenaltyStore
 */
data class CumulativeDiscountedReturn(
    val atomId: String = "DC-DOP-013",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "gamma-discounted sum",
    val processSupervisionDensity: Double = 1.0
) {
    /**
     * (3) Deterministic reward contribution method.
     */
    fun computeReward(): Map<String, Any?>? {
        return mapOf(
            "atomId" to atomId,
            "reward" to rewardValue,
            "timestamp" to timestamp.toString()
        )
    }

    /**
     * (4) Hugging-Face / Parquet compatible serialization method.
     */
    fun toHuggingFaceRecord(): Map<String, Any?> {
        return mapOf(
            "atom_id" to atomId,
            "atom_name" to "CumulativeDiscountedReturn",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "gamma-discounted sum of all base + escalated + amplified signals across the entire trajectory.",
            "research_line" to "Research trajectory-level return for process supervision.",
            "impl_line" to "Emit cumulative discounted return vector paired with terminal verification status.",
            "wire" to "RewardPenaltyStore",
            "payload" to payload,
            "diagnostic_hash" to diagnosticHash,
            "process_supervision_density" to processSupervisionDensity
        )
    }

    /**
     * (5) Lineage fingerprint binding method.
     */
    fun bindFingerprint(promptH0Sha: String, waveId: String, nodeId: String, causalChain: List<String>): Map<String, Any?> {
        val record = toHuggingFaceRecord().toMutableMap()
        record["prompt_h0_sha"] = promptH0Sha
        record["wave_id"] = waveId
        record["node_id"] = nodeId
        record["causal_parent_chain"] = causalChain
        return record
    }
}
