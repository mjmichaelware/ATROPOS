// AUTO-GENERATED TIER-1 ATOM: DC-DOP-001 - BaseApprovedHunkSignal
// HUD: #00FF9F · REWARD: +1.0
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-001: BaseApprovedHunkSignal
 * Category: Dopamine / Reward
 * Expanded Context: Exact token diffs of accepted hunk, files touched, architectural significance, engine intent, operator rejection latency if any, dwell time before acceptance.
 * Research: Research human preference deltas for RLHF alignment against cosmetic over-edits.
 * Implementation: Emit +1.0 RewardEvent; serialize with full token-exact diff. Cap dwell at 30 000 ms.
 * Wire / Core Producers: AgentEditProtocol.kt, AgentPatchApplyService.kt, VerifiedCompletionGate.kt, RewardPenaltyStore, RedactionFilter, IsolatedWorktreeService
 */
data class BaseApprovedHunkSignal(
    val atomId: String = "DC-DOP-001",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "+1.0",
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
            "atom_name" to "BaseApprovedHunkSignal",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Exact token diffs of accepted hunk, files touched, architectural significance, engine intent, operator rejection latency if any, dwell time before acceptance.",
            "research_line" to "Research human preference deltas for RLHF alignment against cosmetic over-edits.",
            "impl_line" to "Emit +1.0 RewardEvent; serialize with full token-exact diff. Cap dwell at 30 000 ms.",
            "wire" to "AgentEditProtocol.kt, AgentPatchApplyService.kt, VerifiedCompletionGate.kt, RewardPenaltyStore, RedactionFilter, IsolatedWorktreeService",
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
