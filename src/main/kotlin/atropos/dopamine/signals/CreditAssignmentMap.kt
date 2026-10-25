// AUTO-GENERATED TIER-1 ATOM: DC-DOP-009 - CreditAssignmentMap
// HUD: #5E5CE6 · REWARD: Backward Propagated
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-009: CreditAssignmentMap
 * Category: Dopamine / Reward
 * Expanded Context: Causal parent pointers for every reward or penalty, propagated values along the causal graph.
 * Research: Research temporal credit assignment for long-horizon RL / AgentPRM.
 * Implementation: Backward propagation using the same 1.2 / 0.15 / 2.0 rules along directed lineage.
 * Wire / Core Producers: RewardPenaltyStore, AutomaticDopamineExecutionListener
 */
data class CreditAssignmentMap(
    val atomId: String = "DC-DOP-009",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Backward Propagated",
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
            "atom_name" to "CreditAssignmentMap",
            "hud_color" to "#5E5CE6",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Causal parent pointers for every reward or penalty, propagated values along the causal graph.",
            "research_line" to "Research temporal credit assignment for long-horizon RL / AgentPRM.",
            "impl_line" to "Backward propagation using the same 1.2 / 0.15 / 2.0 rules along directed lineage.",
            "wire" to "RewardPenaltyStore, AutomaticDopamineExecutionListener",
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
