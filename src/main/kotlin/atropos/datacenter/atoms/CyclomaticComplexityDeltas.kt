// AUTO-GENERATED TIER-1 ATOM: DC-TEL-049 - CyclomaticComplexityDeltas
// HUD: #64D2FF · REWARD: Complexity Delta
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-049: CyclomaticComplexityDeltas
 * Category: Formal / Solver / AST / System
 * Expanded Context: Delta in cyclomatic complexity after an edit across branches and loops.
 * Research: Research software maintainability and complexity drift.
 * Implementation: Emit McCabe complexity delta per mutated function.
 * Wire / Core Producers: TreeSitterGrammarBridge, StaticAnalysisAndLinterDefectHarvester
 */
data class CyclomaticComplexityDeltas(
    val atomId: String = "DC-TEL-049",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Complexity Delta",
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
            "atom_name" to "CyclomaticComplexityDeltas",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Delta in cyclomatic complexity after an edit across branches and loops.",
            "research_line" to "Research software maintainability and complexity drift.",
            "impl_line" to "Emit McCabe complexity delta per mutated function.",
            "wire" to "TreeSitterGrammarBridge, StaticAnalysisAndLinterDefectHarvester",
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
