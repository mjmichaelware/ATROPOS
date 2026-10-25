// AUTO-GENERATED TIER-1 ATOM: DC-DOP-019 - ValueFunctionSnapshot
// HUD: #64D2FF · REWARD: V*(s) Snapshot
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-019: ValueFunctionSnapshot
 * Category: Dopamine / Reward
 * Expanded Context: V*(s) at every wave boundary (Wave-0 to Wave-4) and every major backtrack.
 * Research: Research value-function estimation for long-horizon agents.
 * Implementation: Emit value snapshot at wave boundaries.
 * Wire / Core Producers: AutonomousWaveOrchestrator, RewardPenaltyStore
 */
data class ValueFunctionSnapshot(
    val atomId: String = "DC-DOP-019",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "V*(s) Snapshot",
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
            "atom_name" to "ValueFunctionSnapshot",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "V*(s) at every wave boundary (Wave-0 to Wave-4) and every major backtrack.",
            "research_line" to "Research value-function estimation for long-horizon agents.",
            "impl_line" to "Emit value snapshot at wave boundaries.",
            "wire" to "AutonomousWaveOrchestrator, RewardPenaltyStore",
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
