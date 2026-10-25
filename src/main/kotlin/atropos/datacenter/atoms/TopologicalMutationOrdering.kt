// AUTO-GENERATED TIER-1 ATOM: DC-TEL-148 - TopologicalMutationOrdering
// HUD: #64D2FF · REWARD: Order Optimization
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-148: TopologicalMutationOrdering
 * Category: AST Transformations
 * Expanded Context: Optimal topological sequence for applying multi-file code mutations without breaking intermediate builds.
 * Research: Research transaction ordering in multi-file refactorings.
 * Implementation: Emit topological sort permutation and cycle-breaking rationale.
 * Wire / Core Producers: TopologicalMutation.kt, CodebaseDeltaTreeTracker.kt
 */
data class TopologicalMutationOrdering(
    val atomId: String = "DC-TEL-148",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Order Optimization",
    val processSupervisionDensity: Double = 1.0
) {
    fun computeReward(): Map<String, Any?>? {
        return mapOf(
            "atomId" to atomId,
            "reward" to rewardValue,
            "timestamp" to timestamp.toString()
        )
    }

    fun toHuggingFaceRecord(): Map<String, Any?> {
        return mapOf(
            "atom_id" to atomId,
            "atom_name" to "TopologicalMutationOrdering",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Optimal topological sequence for applying multi-file code mutations without breaking intermediate builds.",
            "research_line" to "Research transaction ordering in multi-file refactorings.",
            "impl_line" to "Emit topological sort permutation and cycle-breaking rationale.",
            "wire" to "TopologicalMutation.kt, CodebaseDeltaTreeTracker.kt",
            "payload" to payload,
            "diagnostic_hash" to diagnosticHash,
            "process_supervision_density" to processSupervisionDensity
        )
    }

    fun bindFingerprint(promptH0Sha: String, waveId: String, nodeId: String, causalChain: List<String>): Map<String, Any?> {
        val record = toHuggingFaceRecord().toMutableMap()
        record["prompt_h0_sha"] = promptH0Sha
        record["wave_id"] = waveId
        record["node_id"] = nodeId
        record["causal_parent_chain"] = causalChain
        return record
    }
}
