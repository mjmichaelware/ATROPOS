// AUTO-GENERATED TIER-1 ATOM: DC-TEL-097 - ManualDiffHunkRejections
// HUD: #FF9500 · REWARD: -1.0
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-097: ManualDiffHunkRejections
 * Category: Formal / Solver / AST / System
 * Expanded Context: Manual diff hunk rejections performed by human reviewer.
 * Research: Research human rejection patterns on fine-grained edits.
 * Implementation: Emit rejected diff hunk and rejection reason.
 * Wire / Core Producers: HumanPreferenceFeedbackCollector, AgentEditProtocol
 */
data class ManualDiffHunkRejections(
    val atomId: String = "DC-TEL-097",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "-1.0",
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
            "atom_name" to "ManualDiffHunkRejections",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Manual diff hunk rejections performed by human reviewer.",
            "research_line" to "Research human rejection patterns on fine-grained edits.",
            "impl_line" to "Emit rejected diff hunk and rejection reason.",
            "wire" to "HumanPreferenceFeedbackCollector, AgentEditProtocol",
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
