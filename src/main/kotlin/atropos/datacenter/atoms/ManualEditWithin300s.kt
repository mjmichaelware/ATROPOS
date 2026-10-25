// AUTO-GENERATED TIER-1 ATOM: DC-TEL-027 - ManualEditWithin300s
// HUD: #FF9500 · REWARD: Token Diffs
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-027: ManualEditWithin300s
 * Category: Human-In-The-Loop
 * Expanded Context: Exact token diffs of any manual edit performed within 300 s of the agent proposal.
 * Research: Research immediate human correction as strong negative preference.
 * Implementation: Emit token-exact diffs of manual edits within 300 s.
 * Wire / Core Producers: AgentEditProtocol.kt, HumanPreferenceFeedbackCollector
 */
data class ManualEditWithin300s(
    val atomId: String = "DC-TEL-027",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Token Diffs",
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
            "atom_name" to "ManualEditWithin300s",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Exact token diffs of any manual edit performed within 300 s of the agent proposal.",
            "research_line" to "Research immediate human correction as strong negative preference.",
            "impl_line" to "Emit token-exact diffs of manual edits within 300 s.",
            "wire" to "AgentEditProtocol.kt, HumanPreferenceFeedbackCollector",
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
