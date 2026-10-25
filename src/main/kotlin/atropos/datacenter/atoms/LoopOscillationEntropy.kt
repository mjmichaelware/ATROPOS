// AUTO-GENERATED TIER-1 ATOM: DC-TEL-005 - LoopOscillationEntropy
// HUD: #FF9500 · REWARD: Entropy Score
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-005: LoopOscillationEntropy
 * Category: Process Supervision & Trajectory
 * Expanded Context: Entropy over tool-call sequences (Meltdown Onset Point style).
 * Research: Research oscillation detection before hard failure.
 * Implementation: Emit entropy score; escalate penalty if oscillation threshold crossed.
 * Wire / Core Producers: AntiOscillation, ToolchainExecutionDriver
 */
data class LoopOscillationEntropy(
    val atomId: String = "DC-TEL-005",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Entropy Score",
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
            "atom_name" to "LoopOscillationEntropy",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Entropy over tool-call sequences (Meltdown Onset Point style).",
            "research_line" to "Research oscillation detection before hard failure.",
            "impl_line" to "Emit entropy score; escalate penalty if oscillation threshold crossed.",
            "wire" to "AntiOscillation, ToolchainExecutionDriver",
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
