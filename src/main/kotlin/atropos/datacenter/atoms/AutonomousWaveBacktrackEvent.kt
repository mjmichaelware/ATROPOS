// AUTO-GENERATED TIER-1 ATOM: DC-TEL-138 - AutonomousWaveBacktrackEvent
// HUD: #FF9500 · REWARD: Backtrack Penalty
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-138: AutonomousWaveBacktrackEvent
 * Category: Autonomous Orchestration
 * Expanded Context: Backtrack depth, pruned decision subtree, recovery cost, and dead-end diagnosis.
 * Research: Research recovery mechanisms and backtracking economics in tree-search agents.
 * Implementation: Emit backtrack depth and discarded token cost.
 * Wire / Core Producers: AutonomousWaveOrchestrator.kt, DagStore.kt
 */
data class AutonomousWaveBacktrackEvent(
    val atomId: String = "DC-TEL-138",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Backtrack Penalty",
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
            "atom_name" to "AutonomousWaveBacktrackEvent",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Backtrack depth, pruned decision subtree, recovery cost, and dead-end diagnosis.",
            "research_line" to "Research recovery mechanisms and backtracking economics in tree-search agents.",
            "impl_line" to "Emit backtrack depth and discarded token cost.",
            "wire" to "AutonomousWaveOrchestrator.kt, DagStore.kt",
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
