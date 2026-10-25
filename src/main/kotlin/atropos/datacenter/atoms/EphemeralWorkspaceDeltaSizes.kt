// AUTO-GENERATED TIER-1 ATOM: DC-TEL-113 - EphemeralWorkspaceDeltaSizes
// HUD: #64D2FF · REWARD: Delta Bytes
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-113: EphemeralWorkspaceDeltaSizes
 * Category: Formal / Solver / AST / System
 * Expanded Context: Ephemeral workspace delta sizes across isolated worktrees.
 * Research: Research worktree lifecycle overhead and memory usage.
 * Implementation: Emit worktree path and byte delta.
 * Wire / Core Producers: IsolatedWorktreeService, WorktreeRecordStore
 */
data class EphemeralWorkspaceDeltaSizes(
    val atomId: String = "DC-TEL-113",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Delta Bytes",
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
            "atom_name" to "EphemeralWorkspaceDeltaSizes",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Ephemeral workspace delta sizes across isolated worktrees.",
            "research_line" to "Research worktree lifecycle overhead and memory usage.",
            "impl_line" to "Emit worktree path and byte delta.",
            "wire" to "IsolatedWorktreeService, WorktreeRecordStore",
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
