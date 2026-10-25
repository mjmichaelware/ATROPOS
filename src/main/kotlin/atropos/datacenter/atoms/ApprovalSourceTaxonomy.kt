// AUTO-GENERATED TIER-1 ATOM: DC-TEL-023 - ApprovalSourceTaxonomy
// HUD: #64D2FF · REWARD: Taxonomy Classification
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-023: ApprovalSourceTaxonomy
 * Category: Human-In-The-Loop
 * Expanded Context: User click versus config rule versus temporary grant versus auto.
 * Research: Research taxonomy of approval sources for preference learning.
 * Implementation: Emit source taxonomy with each approval event.
 * Wire / Core Producers: HumanPreferenceFeedbackCollector, BoundedAgencyGate
 */
data class ApprovalSourceTaxonomy(
    val atomId: String = "DC-TEL-023",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Taxonomy Classification",
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
            "atom_name" to "ApprovalSourceTaxonomy",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "User click versus config rule versus temporary grant versus auto.",
            "research_line" to "Research taxonomy of approval sources for preference learning.",
            "impl_line" to "Emit source taxonomy with each approval event.",
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
