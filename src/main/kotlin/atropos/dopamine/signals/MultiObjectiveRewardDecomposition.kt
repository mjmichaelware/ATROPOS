// AUTO-GENERATED TIER-1 ATOM: DC-DOP-014 - MultiObjectiveRewardDecomposition
// HUD: #64D2FF · REWARD: Multi-channel vector
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-014: MultiObjectiveRewardDecomposition
 * Category: Dopamine / Reward
 * Expanded Context: Separate scalar channels for correctness, efficiency, safety, style, resource; linear combination weights.
 * Research: Research multi-objective RL for software engineering.
 * Implementation: Emit decomposed channels + Pareto combination weights.
 * Wire / Core Producers: RewardPenaltyStore
 */
data class MultiObjectiveRewardDecomposition(
    val atomId: String = "DC-DOP-014",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Multi-channel vector",
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
            "atom_name" to "MultiObjectiveRewardDecomposition",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Separate scalar channels for correctness, efficiency, safety, style, resource; linear combination weights.",
            "research_line" to "Research multi-objective RL for software engineering.",
            "impl_line" to "Emit decomposed channels + Pareto combination weights.",
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
