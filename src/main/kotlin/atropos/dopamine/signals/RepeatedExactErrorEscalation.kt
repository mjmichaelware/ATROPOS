// AUTO-GENERATED TIER-1 ATOM: DC-DOP-006 - RepeatedExactErrorEscalation
// HUD: #FF9500 · REWARD: x1.2 per occurrence
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-006: RepeatedExactErrorEscalation
 * Category: Dopamine / Reward
 * Expanded Context: Exact diagnostic hash or AST-edit fingerprint, occurrence count, prior penalty value.
 * Research: Research neural-pathway pruning of repeated mistakes.
 * Implementation: Multiply prior penalty by 1.2, record new value and occurrence count. Deterministic exact decimal string.
 * Wire / Core Producers: DiagnosticFingerprint, TarjanGraphClusterer, RewardPenaltyStore
 */
data class RepeatedExactErrorEscalation(
    val atomId: String = "DC-DOP-006",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "x1.2 per occurrence",
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
            "atom_name" to "RepeatedExactErrorEscalation",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Exact diagnostic hash or AST-edit fingerprint, occurrence count, prior penalty value.",
            "research_line" to "Research neural-pathway pruning of repeated mistakes.",
            "impl_line" to "Multiply prior penalty by 1.2, record new value and occurrence count. Deterministic exact decimal string.",
            "wire" to "DiagnosticFingerprint, TarjanGraphClusterer, RewardPenaltyStore",
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
