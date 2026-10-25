// AUTO-GENERATED TIER-1 ATOM: DC-TEL-060 - RawFileByteSizeDeltas
// HUD: #64D2FF · REWARD: Byte Delta
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-060: RawFileByteSizeDeltas
 * Category: Formal / Solver / AST / System
 * Expanded Context: Raw byte-size change of source files post-patch application.
 * Research: Research code bloat vs conciseness in agent edits.
 * Implementation: Emit net byte delta across touched files.
 * Wire / Core Producers: IsolatedWorktreeService, CodebaseDeltaTreeTracker
 */
data class RawFileByteSizeDeltas(
    val atomId: String = "DC-TEL-060",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Byte Delta",
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
            "atom_name" to "RawFileByteSizeDeltas",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Raw byte-size change of source files post-patch application.",
            "research_line" to "Research code bloat vs conciseness in agent edits.",
            "impl_line" to "Emit net byte delta across touched files.",
            "wire" to "IsolatedWorktreeService, CodebaseDeltaTreeTracker",
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
