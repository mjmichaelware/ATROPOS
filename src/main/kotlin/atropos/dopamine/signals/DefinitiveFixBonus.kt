// AUTO-GENERATED TIER-1 ATOM: DC-DOP-008 - DefinitiveFixBonus
// HUD: #00FF9F · REWARD: +2.0 (or higher)
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-008: DefinitiveFixBonus
 * Category: Dopamine / Reward
 * Expanded Context: Zero-reoccurrence window length (>= N steps), mutation-kill confirmation of protective assertion.
 * Research: Research permanent corrective invariant formation.
 * Implementation: Emit +2.0 (or higher). This single positive can outweigh prior negatives.
 * Wire / Core Producers: MutationTestingOracle.kt, VerifiedCompletionGate, RewardPenaltyStore
 */
data class DefinitiveFixBonus(
    val atomId: String = "DC-DOP-008",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "+2.0 (or higher)",
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
            "atom_name" to "DefinitiveFixBonus",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Zero-reoccurrence window length (>= N steps), mutation-kill confirmation of protective assertion.",
            "research_line" to "Research permanent corrective invariant formation.",
            "impl_line" to "Emit +2.0 (or higher). This single positive can outweigh prior negatives.",
            "wire" to "MutationTestingOracle.kt, VerifiedCompletionGate, RewardPenaltyStore",
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
