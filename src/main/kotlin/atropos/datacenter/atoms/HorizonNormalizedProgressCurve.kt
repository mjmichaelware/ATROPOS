// AUTO-GENERATED TIER-1 ATOM: DC-TEL-008 - HorizonNormalizedProgressCurve
// HUD: #64D2FF · REWARD: Curve Vector
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-008: HorizonNormalizedProgressCurve
 * Category: Process Supervision & Trajectory
 * Expanded Context: Fraction of obligation DAG nodes completed versus wall-clock and versus token budget.
 * Research: Research horizon-normalized progress for multi-day runs.
 * Implementation: Emit progress curve vector sampled at regular step intervals.
 * Wire / Core Producers: AutonomousWaveOrchestrator, DagStore
 */
data class HorizonNormalizedProgressCurve(
    val atomId: String = "DC-TEL-008",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Curve Vector",
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
            "atom_name" to "HorizonNormalizedProgressCurve",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Fraction of obligation DAG nodes completed versus wall-clock and versus token budget.",
            "research_line" to "Research horizon-normalized progress for multi-day runs.",
            "impl_line" to "Emit progress curve vector sampled at regular step intervals.",
            "wire" to "AutonomousWaveOrchestrator, DagStore",
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
