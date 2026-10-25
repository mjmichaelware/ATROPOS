// AUTO-GENERATED TIER-1 ATOM: DC-DOP-010 - HabituationFactor
// HUD: #FFD60A · REWARD: x0.9 per repetition
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-010: HabituationFactor
 * Category: Dopamine / Reward
 * Expanded Context: Repetition count of identical positive signals within a short window.
 * Research: Research diminishing returns to prevent low-signal noise in the corpus.
 * Implementation: Apply 0.9 diminishing factor per repetition so dataset maintains high entropy.
 * Wire / Core Producers: RewardPenaltyStore
 */
data class HabituationFactor(
    val atomId: String = "DC-DOP-010",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "x0.9 per repetition",
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
            "atom_name" to "HabituationFactor",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Repetition count of identical positive signals within a short window.",
            "research_line" to "Research diminishing returns to prevent low-signal noise in the corpus.",
            "impl_line" to "Apply 0.9 diminishing factor per repetition so dataset maintains high entropy.",
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
