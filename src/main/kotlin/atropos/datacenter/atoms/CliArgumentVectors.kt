// AUTO-GENERATED TIER-1 ATOM: DC-TEL-061 - CliArgumentVectors
// HUD: #64D2FF · REWARD: Argv Vector
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-061: CliArgumentVectors
 * Category: Formal / Solver / AST / System
 * Expanded Context: Exact argv of every tool invocation and sub-process execution.
 * Research: Research tool invocation parameterization and syntax.
 * Implementation: Emit sanitized argv vector.
 * Wire / Core Producers: BoundedProcessRunner, ToolchainExecutionDriver, FileMentionExpander
 */
data class CliArgumentVectors(
    val atomId: String = "DC-TEL-061",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Argv Vector",
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
            "atom_name" to "CliArgumentVectors",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Exact argv of every tool invocation and sub-process execution.",
            "research_line" to "Research tool invocation parameterization and syntax.",
            "impl_line" to "Emit sanitized argv vector.",
            "wire" to "BoundedProcessRunner, ToolchainExecutionDriver, FileMentionExpander",
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
