// AUTO-GENERATED TIER-1 ATOM: DC-TEL-017 - ContextWindowUtilizationFraction
// HUD: #64D2FF · REWARD: Fraction + Events
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-017: ContextWindowUtilizationFraction
 * Category: Token / Context / Efficiency
 * Expanded Context: Fraction of the context window consumed + compaction / summarization events.
 * Research: Research context window health for long-horizon agents.
 * Implementation: Emit utilization fraction and compaction events.
 * Wire / Core Producers: AgentContextCollector.kt, ContextEnvelope, CodebaseContextPacker
 */
data class ContextWindowUtilizationFraction(
    val atomId: String = "DC-TEL-017",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Fraction + Events",
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
            "atom_name" to "ContextWindowUtilizationFraction",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Fraction of the context window consumed + compaction / summarization events.",
            "research_line" to "Research context window health for long-horizon agents.",
            "impl_line" to "Emit utilization fraction and compaction events.",
            "wire" to "AgentContextCollector.kt, ContextEnvelope, CodebaseContextPacker",
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
