// AUTO-GENERATED TIER-1 ATOM: DC-TEL-025 - HandoffSummaryFidelity
// HUD: #00FF9F · REWARD: Fidelity Score
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-025: HandoffSummaryFidelity
 * Category: Human-In-The-Loop
 * Expanded Context: How well the agent summarized state for the next human or agent.
 * Research: Research handoff quality for multi-agent / human-in-the-loop systems.
 * Implementation: Emit fidelity score.
 * Wire / Core Producers: AgentContextExportStore.kt, AgentContextCollector.kt
 */
data class HandoffSummaryFidelity(
    val atomId: String = "DC-TEL-025",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Fidelity Score",
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
            "atom_name" to "HandoffSummaryFidelity",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "How well the agent summarized state for the next human or agent.",
            "research_line" to "Research handoff quality for multi-agent / human-in-the-loop systems.",
            "impl_line" to "Emit fidelity score.",
            "wire" to "AgentContextExportStore.kt, AgentContextCollector.kt",
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
