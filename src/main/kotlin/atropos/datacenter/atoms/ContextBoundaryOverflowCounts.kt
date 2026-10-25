// AUTO-GENERATED TIER-1 ATOM: DC-TEL-117 - ContextBoundaryOverflowCounts
// HUD: #FF3B30 · REWARD: Overflow Count
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-117: ContextBoundaryOverflowCounts
 * Category: Formal / Solver / AST / System
 * Expanded Context: Context-boundary overflow counts and prompt clipping events.
 * Research: Research prompt budget safety margins.
 * Implementation: Emit overflow count and dropped token count.
 * Wire / Core Producers: AgentContextCollector, ContextEnvelope
 */
data class ContextBoundaryOverflowCounts(
    val atomId: String = "DC-TEL-117",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Overflow Count",
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
            "atom_name" to "ContextBoundaryOverflowCounts",
            "hud_color" to "#FF3B30",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Context-boundary overflow counts and prompt clipping events.",
            "research_line" to "Research prompt budget safety margins.",
            "impl_line" to "Emit overflow count and dropped token count.",
            "wire" to "AgentContextCollector, ContextEnvelope",
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
