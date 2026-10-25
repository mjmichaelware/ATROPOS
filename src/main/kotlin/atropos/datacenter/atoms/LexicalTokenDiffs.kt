// AUTO-GENERATED TIER-1 ATOM: DC-TEL-046 - LexicalTokenDiffs
// HUD: #00FF9F · REWARD: +1.0 / -1.0
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-046: LexicalTokenDiffs
 * Category: Formal / Solver / AST / System
 * Expanded Context: Dwell time (ms), manual edits within 300s, cancellation signals, token-exact diffs for rejected hunks, +1.0/-1.0 reward.
 * Research: Research lexical token transformations and human rejection deltas.
 * Implementation: Emit exact token diff; tag approved vs rejected hunks.
 * Wire / Core Producers: AgentEditProtocol.kt, HumanPreferenceFeedbackCollector
 */
data class LexicalTokenDiffs(
    val atomId: String = "DC-TEL-046",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "+1.0 / -1.0",
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
            "atom_name" to "LexicalTokenDiffs",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Dwell time (ms), manual edits within 300s, cancellation signals, token-exact diffs for rejected hunks, +1.0/-1.0 reward.",
            "research_line" to "Research lexical token transformations and human rejection deltas.",
            "impl_line" to "Emit exact token diff; tag approved vs rejected hunks.",
            "wire" to "AgentEditProtocol.kt, HumanPreferenceFeedbackCollector",
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
