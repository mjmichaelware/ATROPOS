// AUTO-GENERATED TIER-1 ATOM: DC-TEL-101 - HumanApprovalLatency
// HUD: #64D2FF · REWARD: Latency ms
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-101: HumanApprovalLatency
 * Category: Formal / Solver / AST / System
 * Expanded Context: Human approval latency for gated action proposals.
 * Research: Research human-in-the-loop decision latency.
 * Implementation: Emit latency in milliseconds.
 * Wire / Core Producers: HumanPreferenceFeedbackCollector, BoundedAgencyGate
 */
data class HumanApprovalLatency(
    val atomId: String = "DC-TEL-101",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Latency ms",
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
            "atom_name" to "HumanApprovalLatency",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Human approval latency for gated action proposals.",
            "research_line" to "Research human-in-the-loop decision latency.",
            "impl_line" to "Emit latency in milliseconds.",
            "wire" to "HumanPreferenceFeedbackCollector, BoundedAgencyGate",
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
