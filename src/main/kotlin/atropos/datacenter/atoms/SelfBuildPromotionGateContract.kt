// AUTO-GENERATED TIER-1 ATOM: DC-TEL-146 - SelfBuildPromotionGateContract
// HUD: #BF5AF2 · REWARD: Promotion Attestation
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-146: SelfBuildPromotionGateContract
 * Category: Release Governance
 * Expanded Context: Promotion contract gating candidate JAR release swaps after full headless CI validation.
 * Research: Research autonomous release management and atomic binary hot-swapping.
 * Implementation: Emit candidate JAR SHA-256, verification signature, and promotion verdict.
 * Wire / Core Producers: SafeJarSwapGate.kt, ReleaseGateEvaluator.kt
 */
data class SelfBuildPromotionGateContract(
    val atomId: String = "DC-TEL-146",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Promotion Attestation",
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
            "atom_name" to "SelfBuildPromotionGateContract",
            "hud_color" to "#BF5AF2",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Promotion contract gating candidate JAR release swaps after full headless CI validation.",
            "research_line" to "Research autonomous release management and atomic binary hot-swapping.",
            "impl_line" to "Emit candidate JAR SHA-256, verification signature, and promotion verdict.",
            "wire" to "SafeJarSwapGate.kt, ReleaseGateEvaluator.kt",
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
