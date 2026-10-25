// AUTO-GENERATED TIER-1 ATOM: DC-TEL-098 - PromptReformulationEditDistance
// HUD: #5E5CE6 · REWARD: Distance
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-098: PromptReformulationEditDistance
 * Category: Formal / Solver / AST / System
 * Expanded Context: Prompt reformulation edit distance across iterative user turns.
 * Research: Research user intent clarification trajectories.
 * Implementation: Emit Levenshtein edit distance.
 * Wire / Core Producers: HumanPreferenceFeedbackCollector
 */
data class PromptReformulationEditDistance(
    val atomId: String = "DC-TEL-098",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Distance",
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
            "atom_name" to "PromptReformulationEditDistance",
            "hud_color" to "#5E5CE6",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Prompt reformulation edit distance across iterative user turns.",
            "research_line" to "Research user intent clarification trajectories.",
            "impl_line" to "Emit Levenshtein edit distance.",
            "wire" to "HumanPreferenceFeedbackCollector",
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
