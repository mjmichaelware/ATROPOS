// AUTO-GENERATED TIER-1 ATOM: DC-TEL-024 - HumanOverrideLatencyAndReason
// HUD: #FF9500 · REWARD: Latency + Reason
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-024: HumanOverrideLatencyAndReason
 * Category: Human-In-The-Loop
 * Expanded Context: Latency of human rejection and free-text or coded reason.
 * Research: Research human override as negative preference signal.
 * Implementation: Emit latency + reason.
 * Wire / Core Producers: HumanPreferenceFeedbackCollector, BoundedRenderingController
 */
data class HumanOverrideLatencyAndReason(
    val atomId: String = "DC-TEL-024",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Latency + Reason",
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
            "atom_name" to "HumanOverrideLatencyAndReason",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Latency of human rejection and free-text or coded reason.",
            "research_line" to "Research human override as negative preference signal.",
            "impl_line" to "Emit latency + reason.",
            "wire" to "HumanPreferenceFeedbackCollector, BoundedRenderingController",
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
