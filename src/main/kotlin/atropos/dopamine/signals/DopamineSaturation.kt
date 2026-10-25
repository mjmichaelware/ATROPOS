// AUTO-GENERATED TIER-1 ATOM: DC-DOP-021 - DopamineSaturation
// HUD: #FFD60A · REWARD: Saturation State
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-021: DopamineSaturation
 * Category: Dopamine / Reward
 * Expanded Context: Habituation state of the reward pathway; rolling window reward variance and ceiling.
 * Research: Research saturation / habituation in neural reward systems.
 * Implementation: Emit saturation state and throttle low-entropy repetitions.
 * Wire / Core Producers: RewardPenaltyStore
 */
data class DopamineSaturation(
    val atomId: String = "DC-DOP-021",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Saturation State",
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
            "atom_name" to "DopamineSaturation",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Habituation state of the reward pathway; rolling window reward variance and ceiling.",
            "research_line" to "Research saturation / habituation in neural reward systems.",
            "impl_line" to "Emit saturation state and throttle low-entropy repetitions.",
            "wire" to "RewardPenaltyStore",
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
