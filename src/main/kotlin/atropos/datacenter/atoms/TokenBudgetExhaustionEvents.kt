// AUTO-GENERATED TIER-1 ATOM: DC-TEL-099 - TokenBudgetExhaustionEvents
// HUD: #FF3B30 · REWARD: Exhaustion Event
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-099: TokenBudgetExhaustionEvents
 * Category: Formal / Solver / AST / System
 * Expanded Context: Token-budget exhaustion events and truncation warnings.
 * Research: Research context window boundary handling.
 * Implementation: Emit token exhaustion event with provider identifier.
 * Wire / Core Producers: AgentContextCollector, ContextEnvelope, ProviderCascadeRouter
 */
data class TokenBudgetExhaustionEvents(
    val atomId: String = "DC-TEL-099",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Exhaustion Event",
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
            "atom_name" to "TokenBudgetExhaustionEvents",
            "hud_color" to "#FF3B30",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Token-budget exhaustion events and truncation warnings.",
            "research_line" to "Research context window boundary handling.",
            "impl_line" to "Emit token exhaustion event with provider identifier.",
            "wire" to "AgentContextCollector, ContextEnvelope, ProviderCascadeRouter",
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
