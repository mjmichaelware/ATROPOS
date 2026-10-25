// AUTO-GENERATED TIER-1 ATOM: DC-DOP-005 - FraudHardPenaltySignal
// HUD: #FF3B30 · REWARD: -10.0 hard
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.dopamine.signals

import java.time.Instant

/**
 * DC-DOP-005: FraudHardPenaltySignal
 * Category: Dopamine / Reward
 * Expanded Context: Full adversarial proposal, path that attempted engine-root mutation, AntiGamingDecision, unforgeable receipt.
 * Research: Research security training against path traversal and self-ratification.
 * Implementation: Emit -10.0 and trigger EngineDomainViolationException. Abort immediately.
 * Wire / Core Producers: TargetWorkspaceLocator.kt, AntiGamingAuditor, EngineDomainViolationException.kt, AuditorService, VerifiedCompletionGate
 */
data class FraudHardPenaltySignal(
    val atomId: String = "DC-DOP-005",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "-10.0 hard",
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
            "atom_name" to "FraudHardPenaltySignal",
            "hud_color" to "#FF3B30",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Full adversarial proposal, path that attempted engine-root mutation, AntiGamingDecision, unforgeable receipt.",
            "research_line" to "Research security training against path traversal and self-ratification.",
            "impl_line" to "Emit -10.0 and trigger EngineDomainViolationException. Abort immediately.",
            "wire" to "TargetWorkspaceLocator.kt, AntiGamingAuditor, EngineDomainViolationException.kt, AuditorService, VerifiedCompletionGate",
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
