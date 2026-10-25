// AUTO-GENERATED TIER-1 ATOM: DC-DOP-016 - PreferencePairMetadata
// HUD: #64D2FF · REWARD: Pair + Margin
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-016: PreferencePairMetadata
 * Category: Dopamine / Reward
 * Expanded Context: Which two full trajectories were ranked, margin of preference, human or automated judge identity.
 * Research: Research preference-pair generation for direct preference optimization.
 * Implementation: Emit pair metadata + margin + divergence index.
 * Wire / Core Producers: RewardPenaltyStore, HumanPreferenceFeedbackCollector
 */
data class PreferencePairMetadata(
    val atomId: String = "DC-DOP-016",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Pair + Margin",
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
            "atom_name" to "PreferencePairMetadata",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Which two full trajectories were ranked, margin of preference, human or automated judge identity.",
            "research_line" to "Research preference-pair generation for direct preference optimization.",
            "impl_line" to "Emit pair metadata + margin + divergence index.",
            "wire" to "RewardPenaltyStore, HumanPreferenceFeedbackCollector",
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
