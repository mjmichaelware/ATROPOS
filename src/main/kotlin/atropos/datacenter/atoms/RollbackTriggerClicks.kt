// AUTO-GENERATED TIER-1 ATOM: DC-TEL-102 - RollbackTriggerClicks
// HUD: #FF9500 · REWARD: Rollback Click
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-102: RollbackTriggerClicks
 * Category: Formal / Solver / AST / System
 * Expanded Context: Rollback trigger clicks reverting workspace to prior commit.
 * Research: Research error recovery and state restoration actions.
 * Implementation: Emit rollback target SHA and trigger timestamp.
 * Wire / Core Producers: IsolatedWorktreeService, HumanPreferenceFeedbackCollector
 */
data class RollbackTriggerClicks(
    val atomId: String = "DC-TEL-102",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Rollback Click",
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
            "atom_name" to "RollbackTriggerClicks",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Rollback trigger clicks reverting workspace to prior commit.",
            "research_line" to "Research error recovery and state restoration actions.",
            "impl_line" to "Emit rollback target SHA and trigger timestamp.",
            "wire" to "IsolatedWorktreeService, HumanPreferenceFeedbackCollector",
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
