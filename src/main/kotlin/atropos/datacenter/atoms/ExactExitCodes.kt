// AUTO-GENERATED TIER-1 ATOM: DC-TEL-063 - ExactExitCodes
// HUD: #64D2FF · REWARD: Exit Code
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-063: ExactExitCodes
 * Category: Formal / Solver / AST / System
 * Expanded Context: Exact process exit code (0, 1, 127, 137, etc.).
 * Research: Research execution failure modes and return codes.
 * Implementation: Emit exact exit code integer.
 * Wire / Core Producers: BoundedProcessRunner, ToolchainExecutionDriver
 */
data class ExactExitCodes(
    val atomId: String = "DC-TEL-063",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Exit Code",
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
            "atom_name" to "ExactExitCodes",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Exact process exit code (0, 1, 127, 137, etc.).",
            "research_line" to "Research execution failure modes and return codes.",
            "impl_line" to "Emit exact exit code integer.",
            "wire" to "BoundedProcessRunner, ToolchainExecutionDriver",
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
