// AUTO-GENERATED TIER-1 ATOM: DC-TEL-044 - OperatorGazeOrCursorDwellHeatmap
// HUD: #64D2FF · REWARD: Heatmap Tensor
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-044: OperatorGazeOrCursorDwellHeatmap
 * Category: Multimodal & Temporal
 * Expanded Context: Aggregated gaze or cursor-dwell heatmap over the same temporal window as the agent's reasoning trace.
 * Research: Research operator attention as preference signal.
 * Implementation: Emit heatmap over temporal window.
 * Wire / Core Producers: BoundedRenderingController, HudRules, HumanPreferenceFeedbackCollector
 */
data class OperatorGazeOrCursorDwellHeatmap(
    val atomId: String = "DC-TEL-044",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Heatmap Tensor",
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
            "atom_name" to "OperatorGazeOrCursorDwellHeatmap",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Aggregated gaze or cursor-dwell heatmap over the same temporal window as the agent's reasoning trace.",
            "research_line" to "Research operator attention as preference signal.",
            "impl_line" to "Emit heatmap over temporal window.",
            "wire" to "BoundedRenderingController, HudRules, HumanPreferenceFeedbackCollector",
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
