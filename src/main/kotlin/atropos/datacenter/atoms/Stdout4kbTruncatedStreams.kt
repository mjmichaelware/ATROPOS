// AUTO-GENERATED TIER-1 ATOM: DC-TEL-062 - Stdout4kbTruncatedStreams
// HUD: #64D2FF · REWARD: 4KB Stream Digest
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-062: Stdout4kbTruncatedStreams
 * Category: Formal / Solver / AST / System
 * Expanded Context: 4 KB truncated stdout / stderr captured from tool runs.
 * Research: Research error stream diagnosis and output interpretation.
 * Implementation: Emit 4KB truncated stream digest.
 * Wire / Core Producers: BoundedProcessRunner, ToolchainExecutionDriver, ChrootUnshareJail
 */
data class Stdout4kbTruncatedStreams(
    val atomId: String = "DC-TEL-062",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "4KB Stream Digest",
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
            "atom_name" to "Stdout4kbTruncatedStreams",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "4 KB truncated stdout / stderr captured from tool runs.",
            "research_line" to "Research error stream diagnosis and output interpretation.",
            "impl_line" to "Emit 4KB truncated stream digest.",
            "wire" to "BoundedProcessRunner, ToolchainExecutionDriver, ChrootUnshareJail",
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
