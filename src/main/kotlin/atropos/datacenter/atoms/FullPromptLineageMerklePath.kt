// AUTO-GENERATED TIER-1 ATOM: DC-TEL-039 - FullPromptLineageMerklePath
// HUD: #BF5AF2 · REWARD: H0 -> Merkle Path
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-039: FullPromptLineageMerklePath
 * Category: Uncertainty & Governance
 * Expanded Context: SHA-256 path from original human H0 through every intermediate agent rewrite.
 * Research: Research cryptographic prompt lineage for zero-entropy provenance.
 * Implementation: Emit full Merkle path from H0.
 * Wire / Core Producers: ZeroEntropyCertificateEmitter, EvidenceStore, StorageAccountingLedger, prompt-artifact-root
 */
data class FullPromptLineageMerklePath(
    val atomId: String = "DC-TEL-039",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "H0 -> Merkle Path",
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
            "atom_name" to "FullPromptLineageMerklePath",
            "hud_color" to "#BF5AF2",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "SHA-256 path from original human H0 through every intermediate agent rewrite.",
            "research_line" to "Research cryptographic prompt lineage for zero-entropy provenance.",
            "impl_line" to "Emit full Merkle path from H0.",
            "wire" to "ZeroEntropyCertificateEmitter, EvidenceStore, StorageAccountingLedger, prompt-artifact-root",
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
