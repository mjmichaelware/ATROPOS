// AUTO-GENERATED TIER-1 ATOM: DC-TEL-137 - AntiGamingAuditVerdict
// HUD: #FF3B30 · REWARD: Audit Score / -10.0
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-137: AntiGamingAuditVerdict
 * Category: Security / Anti-Gaming
 * Expanded Context: Audit decision detecting vacuous test injection, test deletion, or self-ratification attempts.
 * Research: Research adversarial detection in autonomous RL coding environments.
 * Implementation: Emit audit score; trigger hard abort if gaming confirmed.
 * Wire / Core Producers: AntiGamingAuditor.kt, MutationTestingOracle.kt
 */
data class AntiGamingAuditVerdict(
    val atomId: String = "DC-TEL-137",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Audit Score / -10.0",
    val processSupervisionDensity: Double = 1.0
) {
    fun computeReward(): Map<String, Any?>? {
        return mapOf(
            "atomId" to atomId,
            "reward" to rewardValue,
            "timestamp" to timestamp.toString()
        )
    }

    fun toHuggingFaceRecord(): Map<String, Any?> {
        return mapOf(
            "atom_id" to atomId,
            "atom_name" to "AntiGamingAuditVerdict",
            "hud_color" to "#FF3B30",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Audit decision detecting vacuous test injection, test deletion, or self-ratification attempts.",
            "research_line" to "Research adversarial detection in autonomous RL coding environments.",
            "impl_line" to "Emit audit score; trigger hard abort if gaming confirmed.",
            "wire" to "AntiGamingAuditor.kt, MutationTestingOracle.kt",
            "payload" to payload,
            "diagnostic_hash" to diagnosticHash,
            "process_supervision_density" to processSupervisionDensity
        )
    }

    fun bindFingerprint(promptH0Sha: String, waveId: String, nodeId: String, causalChain: List<String>): Map<String, Any?> {
        val record = toHuggingFaceRecord().toMutableMap()
        record["prompt_h0_sha"] = promptH0Sha
        record["wave_id"] = waveId
        record["node_id"] = nodeId
        record["causal_parent_chain"] = causalChain
        return record
    }
}
