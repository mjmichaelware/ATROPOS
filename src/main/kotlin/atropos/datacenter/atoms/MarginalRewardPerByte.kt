// AUTO-GENERATED TIER-1 ATOM: DC-TEL-016 - MarginalRewardPerByte
// HUD: #FFD60A · REWARD: Reward / Byte
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-016: MarginalRewardPerByte
 * Category: Token / Context / Efficiency
 * Expanded Context: Reward density of each Parquet write to the 100 GB corpus.
 * Research: Research byte-level efficiency of the living neural substrate.
 * Implementation: Emit marginal reward per byte written to storage.
 * Wire / Core Producers: DynamicDatasetSyncService, EvidenceStore, StorageAccountingLedger
 */
data class MarginalRewardPerByte(
    val atomId: String = "DC-TEL-016",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Reward / Byte",
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
            "atom_name" to "MarginalRewardPerByte",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Reward density of each Parquet write to the 100 GB corpus.",
            "research_line" to "Research byte-level efficiency of the living neural substrate.",
            "impl_line" to "Emit marginal reward per byte written to storage.",
            "wire" to "DynamicDatasetSyncService, EvidenceStore, StorageAccountingLedger",
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
