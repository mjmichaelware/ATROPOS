// AUTO-GENERATED TIER-1 ATOM: DC-TEL-096 - CssBoundingBoxDeltas
// HUD: #64D2FF · REWARD: Bounding Box Delta
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-096: CssBoundingBoxDeltas
 * Category: Formal / Solver / AST / System
 * Expanded Context: CSS bounding-box deltas between baseline and agent-rendered UI.
 * Research: Research visual layout stability and reflow bugs.
 * Implementation: Emit bounding box coordinates shift.
 * Wire / Core Producers: LivePreviewService, MultimodalDomAndVisualLayoutHarvester
 */
data class CssBoundingBoxDeltas(
    val atomId: String = "DC-TEL-096",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Bounding Box Delta",
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
            "atom_name" to "CssBoundingBoxDeltas",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "CSS bounding-box deltas between baseline and agent-rendered UI.",
            "research_line" to "Research visual layout stability and reflow bugs.",
            "impl_line" to "Emit bounding box coordinates shift.",
            "wire" to "LivePreviewService, MultimodalDomAndVisualLayoutHarvester",
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
