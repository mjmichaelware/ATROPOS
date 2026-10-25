// AUTO-GENERATED TIER-1 ATOM: DC-TEL-045 - VisualRegressionPixelMetricsLinkedToAst
// HUD: #64D2FF · REWARD: Pixel Metrics + AST
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-045: VisualRegressionPixelMetricsLinkedToAst
 * Category: Multimodal & Temporal
 * Expanded Context: Pixel-level visual regression metrics linked to the AST or DOM change that produced them.
 * Research: Research visual regression as process-supervision signal.
 * Implementation: Emit pixel metrics linked to AST/DOM change.
 * Wire / Core Producers: LivePreviewService, BrowserActuator, VisualComparison, TreeSitterGrammarBridge
 */
data class VisualRegressionPixelMetricsLinkedToAst(
    val atomId: String = "DC-TEL-045",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Pixel Metrics + AST",
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
            "atom_name" to "VisualRegressionPixelMetricsLinkedToAst",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Pixel-level visual regression metrics linked to the AST or DOM change that produced them.",
            "research_line" to "Research visual regression as process-supervision signal.",
            "impl_line" to "Emit pixel metrics linked to AST/DOM change.",
            "wire" to "LivePreviewService, BrowserActuator, VisualComparison, TreeSitterGrammarBridge",
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
