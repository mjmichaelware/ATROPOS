// AUTO-GENERATED TIER-1 ATOM: DC-DOP-011 - AdvantageEstimate
// HUD: #64D2FF · REWARD: A(s,a) Scalar
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-011: AdvantageEstimate
 * Category: Dopamine / Reward
 * Expanded Context: Advantage estimate A(s,a) = Q(s,a) - V(s) at every decision point along the trajectory.
 * Research: Research actor-critic process supervision for code synthesis.
 * Implementation: Emit advantage estimate at each decision point; serialize with state snapshot.
 * Wire / Core Producers: RewardPenaltyStore, ThinkingStream
 */
data class AdvantageEstimate(
    val atomId: String = "DC-DOP-011",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "A(s,a) Scalar",
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
            "atom_name" to "AdvantageEstimate",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Advantage estimate A(s,a) = Q(s,a) - V(s) at every decision point along the trajectory.",
            "research_line" to "Research actor-critic process supervision for code synthesis.",
            "impl_line" to "Emit advantage estimate at each decision point; serialize with state snapshot.",
            "wire" to "RewardPenaltyStore, ThinkingStream",
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
