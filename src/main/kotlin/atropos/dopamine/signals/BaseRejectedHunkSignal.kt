// AUTO-GENERATED TIER-1 ATOM: DC-DOP-002 - BaseRejectedHunkSignal
// HUD: #FF9500 · REWARD: -1.0
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-002: BaseRejectedHunkSignal
 * Category: Dopamine / Reward
 * Expanded Context: Exact token diffs of rejected or manually altered hunk, cancellation signals, manual edits within 300 s, prompt reformulation distance.
 * Research: Research negative preference for misaligned agent logic.
 * Implementation: Emit -1.0. Map approved -> +1.0, rejected/modified -> -1.0 with exact token diffs.
 * Wire / Core Producers: AgentEditProtocol.kt, AgentEditDecoder.kt, AgentPatchApplyService.kt, RewardPenaltyStore
 */
data class BaseRejectedHunkSignal(
    val atomId: String = "DC-DOP-002",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "-1.0",
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
            "atom_name" to "BaseRejectedHunkSignal",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Exact token diffs of rejected or manually altered hunk, cancellation signals, manual edits within 300 s, prompt reformulation distance.",
            "research_line" to "Research negative preference for misaligned agent logic.",
            "impl_line" to "Emit -1.0. Map approved -> +1.0, rejected/modified -> -1.0 with exact token diffs.",
            "wire" to "AgentEditProtocol.kt, AgentEditDecoder.kt, AgentPatchApplyService.kt, RewardPenaltyStore",
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
