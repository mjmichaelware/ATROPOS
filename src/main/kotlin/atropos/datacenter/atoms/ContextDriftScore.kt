// AUTO-GENERATED TIER-1 ATOM: DC-TEL-018 - ContextDriftScore
// HUD: #FF9500 · REWARD: Drift Score
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-018: ContextDriftScore
 * Category: Token / Context / Efficiency
 * Expanded Context: Semantic drift of the working context across a long horizon (token-level and embedding-level). Detect when the agent begins to forget earlier instructions or constraints.
 * Research: Research context drift detection for multi-day autonomous runs.
 * Implementation: Compute drift between successive context snapshots; emit when threshold crossed. Wire to Auditor so high-drift trajectories can be prioritized or quarantined.
 * Wire / Core Producers: AgentContextCollector.kt, ContextEnvelope, LocalMemoryStore, ContextAttestationService
 */
data class ContextDriftScore(
    val atomId: String = "DC-TEL-018",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Drift Score",
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
            "atom_name" to "ContextDriftScore",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Semantic drift of the working context across a long horizon (token-level and embedding-level). Detect when the agent begins to forget earlier instructions or constraints.",
            "research_line" to "Research context drift detection for multi-day autonomous runs.",
            "impl_line" to "Compute drift between successive context snapshots; emit when threshold crossed. Wire to Auditor so high-drift trajectories can be prioritized or quarantined.",
            "wire" to "AgentContextCollector.kt, ContextEnvelope, LocalMemoryStore, ContextAttestationService",
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
