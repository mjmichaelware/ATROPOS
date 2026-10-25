// AUTO-GENERATED TIER-1 ATOM: DC-TEL-015 - TokenToRewardEfficiency
// HUD: #FFD60A · REWARD: Points / 1k Tokens
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-015: TokenToRewardEfficiency
 * Category: Token / Context / Efficiency
 * Expanded Context: Reward points earned per 1 000 tokens consumed across inference calls.
 * Research: Research economic density of the training signal.
 * Implementation: Emit efficiency ratio; prioritize high-value negatives under backpressure.
 * Wire / Core Producers: RewardPenaltyStore, TelemetryRingBuffer, StorageSupervisor
 */
data class TokenToRewardEfficiency(
    val atomId: String = "DC-TEL-015",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Points / 1k Tokens",
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
            "atom_name" to "TokenToRewardEfficiency",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Reward points earned per 1 000 tokens consumed across inference calls.",
            "research_line" to "Research economic density of the training signal.",
            "impl_line" to "Emit efficiency ratio; prioritize high-value negatives under backpressure.",
            "wire" to "RewardPenaltyStore, TelemetryRingBuffer, StorageSupervisor",
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
