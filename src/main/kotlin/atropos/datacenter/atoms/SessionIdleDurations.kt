// AUTO-GENERATED TIER-1 ATOM: DC-TEL-100 - SessionIdleDurations
// HUD: #FFFFFF · REWARD: Idle Duration s
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-100: SessionIdleDurations
 * Category: Formal / Solver / AST / System
 * Expanded Context: Session idle durations between agent output and user response.
 * Research: Research operator cognitive dwell and reading time.
 * Implementation: Emit idle duration in seconds.
 * Wire / Core Producers: AgentDaemonService, LocalMemoryStore
 */
data class SessionIdleDurations(
    val atomId: String = "DC-TEL-100",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Idle Duration s",
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
            "atom_name" to "SessionIdleDurations",
            "hud_color" to "#FFFFFF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Session idle durations between agent output and user response.",
            "research_line" to "Research operator cognitive dwell and reading time.",
            "impl_line" to "Emit idle duration in seconds.",
            "wire" to "AgentDaemonService, LocalMemoryStore",
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
