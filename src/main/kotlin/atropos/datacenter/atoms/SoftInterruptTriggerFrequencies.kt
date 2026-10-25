// AUTO-GENERATED TIER-1 ATOM: DC-TEL-119 - SoftInterruptTriggerFrequencies
// HUD: #FFD60A · REWARD: Interrupt Freq
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-119: SoftInterruptTriggerFrequencies
 * Category: Formal / Solver / AST / System
 * Expanded Context: Soft-interrupt trigger frequencies under cooperative cancellation.
 * Research: Research responsive cancellation in autonomous agents.
 * Implementation: Emit interrupt count and handler latency.
 * Wire / Core Producers: AgentDaemonService, Soft-Fail Network Law
 */
data class SoftInterruptTriggerFrequencies(
    val atomId: String = "DC-TEL-119",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Interrupt Freq",
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
            "atom_name" to "SoftInterruptTriggerFrequencies",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Soft-interrupt trigger frequencies under cooperative cancellation.",
            "research_line" to "Research responsive cancellation in autonomous agents.",
            "impl_line" to "Emit interrupt count and handler latency.",
            "wire" to "AgentDaemonService, Soft-Fail Network Law",
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
