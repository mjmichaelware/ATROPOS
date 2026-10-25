// AUTO-GENERATED TIER-1 ATOM: DC-TEL-047 - TreeSitterNodeMutations
// HUD: #64D2FF · REWARD: Structural Distance
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-047: TreeSitterNodeMutations
 * Category: Formal / Solver / AST / System
 * Expanded Context: Added/deleted/modified AST nodes, structural edit distance, lockfile/dependency modifications.
 * Research: Research structural AST transformations for program synthesis.
 * Implementation: Emit structural edit distance and mutated AST node hierarchy.
 * Wire / Core Producers: TreeSitterGrammarBridge, CodebaseDeltaTreeTracker, IsolatedWorktreeService
 */
data class TreeSitterNodeMutations(
    val atomId: String = "DC-TEL-047",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Structural Distance",
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
            "atom_name" to "TreeSitterNodeMutations",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Added/deleted/modified AST nodes, structural edit distance, lockfile/dependency modifications.",
            "research_line" to "Research structural AST transformations for program synthesis.",
            "impl_line" to "Emit structural edit distance and mutated AST node hierarchy.",
            "wire" to "TreeSitterGrammarBridge, CodebaseDeltaTreeTracker, IsolatedWorktreeService",
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
