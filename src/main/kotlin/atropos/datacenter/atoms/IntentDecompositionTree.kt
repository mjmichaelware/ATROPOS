// AUTO-GENERATED TIER-1 ATOM: DC-TEL-139 - IntentDecompositionTree
// HUD: #64D2FF · REWARD: Plan Coherence
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-139: IntentDecompositionTree
 * Category: Intent & Planning
 * Expanded Context: High-level intent token hierarchy mapped to leaf tool obligations and execution branches.
 * Research: Research hierarchical task decomposition from ambiguous user prompts.
 * Implementation: Emit decomposition depth, branch count, and sub-goal fulfillment status.
 * Wire / Core Producers: ThinkingStream.kt, AutonomousWaveOrchestrator.kt
 */
data class IntentDecompositionTree(
    val atomId: String = "DC-TEL-139",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Plan Coherence",
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
            "atom_name" to "IntentDecompositionTree",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "High-level intent token hierarchy mapped to leaf tool obligations and execution branches.",
            "research_line" to "Research hierarchical task decomposition from ambiguous user prompts.",
            "impl_line" to "Emit decomposition depth, branch count, and sub-goal fulfillment status.",
            "wire" to "ThinkingStream.kt, AutonomousWaveOrchestrator.kt",
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
