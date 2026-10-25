// AUTO-GENERATED TIER-1 ATOM: DC-DOP-017 - ImplicitPreferenceGradient
// HUD: #64D2FF · REWARD: Gradient Vector
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-017: ImplicitPreferenceGradient
 * Category: Dopamine / Reward
 * Expanded Context: Rate of change of dwell / undo / reformulation signals rather than binary +/-1.
 * Research: Research continuous preference signals from interaction telemetry.
 * Implementation: Emit gradient of preference strength over time window.
 * Wire / Core Producers: HumanPreferenceFeedbackCollector, BoundedRenderingController
 */
data class ImplicitPreferenceGradient(
    val atomId: String = "DC-DOP-017",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Gradient Vector",
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
            "atom_name" to "ImplicitPreferenceGradient",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Rate of change of dwell / undo / reformulation signals rather than binary +/-1.",
            "research_line" to "Research continuous preference signals from interaction telemetry.",
            "impl_line" to "Emit gradient of preference strength over time window.",
            "wire" to "HumanPreferenceFeedbackCollector, BoundedRenderingController",
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
