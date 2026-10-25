// AUTO-GENERATED TIER-1 ATOM: DC-DOP-020 - ExplorationBonusResidual
// HUD: #64D2FF · REWARD: Entropy Residual
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-020: ExplorationBonusResidual
 * Category: Dopamine / Reward
 * Expanded Context: Entropy or novelty term that was added or withheld during tree search.
 * Research: Research exploration bonuses in coding agents.
 * Implementation: Emit residual exploration term linked to AST novelty.
 * Wire / Core Producers: RewardPenaltyStore
 */
data class ExplorationBonusResidual(
    val atomId: String = "DC-DOP-020",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Entropy Residual",
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
            "atom_name" to "ExplorationBonusResidual",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Entropy or novelty term that was added or withheld during tree search.",
            "research_line" to "Research exploration bonuses in coding agents.",
            "impl_line" to "Emit residual exploration term linked to AST novelty.",
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
