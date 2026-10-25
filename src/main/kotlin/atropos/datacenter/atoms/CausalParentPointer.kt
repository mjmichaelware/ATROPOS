// AUTO-GENERATED TIER-1 ATOM: DC-TEL-009 - CausalParentPointer
// HUD: #5E5CE6 · REWARD: Parent Pointer
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-009: CausalParentPointer
 * Category: Process Supervision & Trajectory
 * Expanded Context: For every reward or penalty, the earlier action or observation that is the direct cause.
 * Research: Research causal credit assignment.
 * Implementation: Emit causal parent pointer with each RewardEvent.
 * Wire / Core Producers: RewardPenaltyStore, FingerprintBinder
 */
data class CausalParentPointer(
    val atomId: String = "DC-TEL-009",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Parent Pointer",
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
            "atom_name" to "CausalParentPointer",
            "hud_color" to "#5E5CE6",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "For every reward or penalty, the earlier action or observation that is the direct cause.",
            "research_line" to "Research causal credit assignment.",
            "impl_line" to "Emit causal parent pointer with each RewardEvent.",
            "wire" to "RewardPenaltyStore, FingerprintBinder",
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
