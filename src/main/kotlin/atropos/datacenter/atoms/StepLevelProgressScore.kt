// AUTO-GENERATED TIER-1 ATOM: DC-TEL-001 - StepLevelProgressScore
// HUD: #64D2FF · REWARD: Graded Progress
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-001: StepLevelProgressScore
 * Category: Process Supervision & Trajectory
 * Expanded Context: Partial-credit score along the obligation DAG, fraction of nodes completed vs wall-clock and token budget.
 * Research: Research process supervision for long-horizon agents (AgentPRM style).
 * Implementation: Emit graded progress; Auditor may ship only if density high.
 * Wire / Core Producers: AutonomousWaveOrchestrator, DagStore, VerifiedCompletionGate, TelemetryRingBuffer
 */
data class StepLevelProgressScore(
    val atomId: String = "DC-TEL-001",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Graded Progress",
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
            "atom_name" to "StepLevelProgressScore",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Partial-credit score along the obligation DAG, fraction of nodes completed vs wall-clock and token budget.",
            "research_line" to "Research process supervision for long-horizon agents (AgentPRM style).",
            "impl_line" to "Emit graded progress; Auditor may ship only if density high.",
            "wire" to "AutonomousWaveOrchestrator, DagStore, VerifiedCompletionGate, TelemetryRingBuffer",
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
