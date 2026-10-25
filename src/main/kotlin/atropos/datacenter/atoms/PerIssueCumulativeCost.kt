// AUTO-GENERATED TIER-1 ATOM: DC-TEL-021 - PerIssueCumulativeCost
// HUD: #FFD60A · REWARD: Token + Dollar Cost
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-021: PerIssueCumulativeCost
 * Category: Token / Context / Efficiency
 * Expanded Context: Cumulative token + dollar cost for an entire issue / wave.
 * Research: Research economic tracking of long-horizon issues.
 * Implementation: Emit cumulative cost vector.
 * Wire / Core Producers: RewardPenaltyStore, TelemetryRingBuffer
 */
data class PerIssueCumulativeCost(
    val atomId: String = "DC-TEL-021",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Token + Dollar Cost",
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
            "atom_name" to "PerIssueCumulativeCost",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Cumulative token + dollar cost for an entire issue / wave.",
            "research_line" to "Research economic tracking of long-horizon issues.",
            "impl_line" to "Emit cumulative cost vector.",
            "wire" to "RewardPenaltyStore, TelemetryRingBuffer",
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
