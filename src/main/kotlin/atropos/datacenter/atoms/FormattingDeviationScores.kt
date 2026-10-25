// AUTO-GENERATED TIER-1 ATOM: DC-TEL-052 - FormattingDeviationScores
// HUD: #FF9500 · REWARD: -0.2 Style
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-052: FormattingDeviationScores
 * Category: Formal / Solver / AST / System
 * Expanded Context: Deviation from project formatting rules (ruff/clippy/eslint/ktlint).
 * Research: Research adherence to style and syntactic norms.
 * Implementation: Emit formatting deviation penalty.
 * Wire / Core Producers: StaticAnalysisAndLinterDefectHarvester
 */
data class FormattingDeviationScores(
    val atomId: String = "DC-TEL-052",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "-0.2 Style",
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
            "atom_name" to "FormattingDeviationScores",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Deviation from project formatting rules (ruff/clippy/eslint/ktlint).",
            "research_line" to "Research adherence to style and syntactic norms.",
            "impl_line" to "Emit formatting deviation penalty.",
            "wire" to "StaticAnalysisAndLinterDefectHarvester",
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
