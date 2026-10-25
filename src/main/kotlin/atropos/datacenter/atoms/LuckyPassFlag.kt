// AUTO-GENERATED TIER-1 ATOM: DC-TEL-002 - LuckyPassFlag
// HUD: #FF9500 · REWARD: Flag + Brittleness
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-002: LuckyPassFlag
 * Category: Process Supervision & Trajectory
 * Expanded Context: Correct final outcome reached via brittle or non-generalizable path.
 * Research: Research AgentLens-style process-weakness detection.
 * Implementation: Emit Lucky-Pass flag when outcome is correct but path is brittle.
 * Wire / Core Producers: VerifiedCompletionGate, FalseGreenGuard
 */
data class LuckyPassFlag(
    val atomId: String = "DC-TEL-002",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Flag + Brittleness",
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
            "atom_name" to "LuckyPassFlag",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Correct final outcome reached via brittle or non-generalizable path.",
            "research_line" to "Research AgentLens-style process-weakness detection.",
            "impl_line" to "Emit Lucky-Pass flag when outcome is correct but path is brittle.",
            "wire" to "VerifiedCompletionGate, FalseGreenGuard",
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
