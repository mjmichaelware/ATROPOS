// AUTO-GENERATED TIER-1 ATOM: DC-DOP-007 - RelatedErrorAmplification
// HUD: #5E5CE6 · REWARD: +0.15 value multiplier
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-007: RelatedErrorAmplification
 * Category: Dopamine / Reward
 * Expanded Context: Shared language, Tarjan SCC membership, embedding proximity, causal parent.
 * Research: Research related pain as learning priority amplifier.
 * Implementation: Apply +0.15 value multiplier to both signals when sharing language or failure-mode cluster.
 * Wire / Core Producers: TarjanGraphClusterer, RelationalCycleUnificationPartitioner, RewardPenaltyStore
 */
data class RelatedErrorAmplification(
    val atomId: String = "DC-DOP-007",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "+0.15 value multiplier",
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
            "atom_name" to "RelatedErrorAmplification",
            "hud_color" to "#5E5CE6",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Shared language, Tarjan SCC membership, embedding proximity, causal parent.",
            "research_line" to "Research related pain as learning priority amplifier.",
            "impl_line" to "Apply +0.15 value multiplier to both signals when sharing language or failure-mode cluster.",
            "wire" to "TarjanGraphClusterer, RelationalCycleUnificationPartitioner, RewardPenaltyStore",
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
