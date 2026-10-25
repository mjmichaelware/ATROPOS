// AUTO-GENERATED TIER-1 ATOM: DC-DOP-015 - RewardHackingDetectionScore
// HUD: #FF3B30 · REWARD: Outlier / -10.0 hard
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-015: RewardHackingDetectionScore
 * Category: Dopamine / Reward
 * Expanded Context: Statistical outlier detection on unusually high positive reward achieved with low process-supervision density.
 * Research: Research anti-gaming of the reward system itself.
 * Implementation: Emit hacking detection score; hard penalty if threshold crossed.
 * Wire / Core Producers: AntiGamingAuditor, RewardPenaltyStore
 */
data class RewardHackingDetectionScore(
    val atomId: String = "DC-DOP-015",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Outlier / -10.0 hard",
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
            "atom_name" to "RewardHackingDetectionScore",
            "hud_color" to "#FF3B30",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Statistical outlier detection on unusually high positive reward achieved with low process-supervision density.",
            "research_line" to "Research anti-gaming of the reward system itself.",
            "impl_line" to "Emit hacking detection score; hard penalty if threshold crossed.",
            "wire" to "AntiGamingAuditor, RewardPenaltyStore",
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
