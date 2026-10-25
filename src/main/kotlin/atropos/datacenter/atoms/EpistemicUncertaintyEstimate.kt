// AUTO-GENERATED TIER-1 ATOM: DC-TEL-038 - EpistemicUncertaintyEstimate
// HUD: #64D2FF · REWARD: Uncertainty Estimate
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-038: EpistemicUncertaintyEstimate
 * Category: Uncertainty & Governance
 * Expanded Context: Epistemic uncertainty attached to every major decision or generated patch.
 * Research: Research uncertainty quantification for coding agents.
 * Implementation: Emit epistemic uncertainty estimate.
 * Wire / Core Producers: ConfidenceCalculator, HysteresisConfidenceEngine, ThinkingStream
 */
data class EpistemicUncertaintyEstimate(
    val atomId: String = "DC-TEL-038",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Uncertainty Estimate",
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
            "atom_name" to "EpistemicUncertaintyEstimate",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Epistemic uncertainty attached to every major decision or generated patch.",
            "research_line" to "Research uncertainty quantification for coding agents.",
            "impl_line" to "Emit epistemic uncertainty estimate.",
            "wire" to "ConfidenceCalculator, HysteresisConfidenceEngine, ThinkingStream",
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
