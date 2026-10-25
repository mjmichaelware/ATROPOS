// AUTO-GENERATED TIER-1 ATOM: DC-DOP-012 - TemporalDifferenceResidual
// HUD: #64D2FF · REWARD: delta_t Residual
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-012: TemporalDifferenceResidual
 * Category: Dopamine / Reward
 * Expanded Context: delta_t temporal-difference residual evaluated across successive agent tool turns.
 * Research: Research TD learning for long-horizon trajectories.
 * Implementation: Emit delta_t residual vector with discounted factor gamma.
 * Wire / Core Producers: RewardPenaltyStore
 */
data class TemporalDifferenceResidual(
    val atomId: String = "DC-DOP-012",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "delta_t Residual",
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
            "atom_name" to "TemporalDifferenceResidual",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "delta_t temporal-difference residual evaluated across successive agent tool turns.",
            "research_line" to "Research TD learning for long-horizon trajectories.",
            "impl_line" to "Emit delta_t residual vector with discounted factor gamma.",
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
