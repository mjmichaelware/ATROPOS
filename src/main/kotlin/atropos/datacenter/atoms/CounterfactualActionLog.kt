// AUTO-GENERATED TIER-1 ATOM: DC-TEL-010 - CounterfactualActionLog
// HUD: #64D2FF · REWARD: Top-k Alternatives
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-010: CounterfactualActionLog
 * Category: Process Supervision & Trajectory
 * Expanded Context: The agent's own estimate of what would have happened under the top-k alternative actions it considered but did not take.
 * Research: Research counterfactual reasoning for process supervision.
 * Implementation: Emit top-k counterfactuals with expected value estimates.
 * Wire / Core Producers: ThinkingStream, ProviderCascadeRouter
 */
data class CounterfactualActionLog(
    val atomId: String = "DC-TEL-010",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Top-k Alternatives",
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
            "atom_name" to "CounterfactualActionLog",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "The agent's own estimate of what would have happened under the top-k alternative actions it considered but did not take.",
            "research_line" to "Research counterfactual reasoning for process supervision.",
            "impl_line" to "Emit top-k counterfactuals with expected value estimates.",
            "wire" to "ThinkingStream, ProviderCascadeRouter",
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
