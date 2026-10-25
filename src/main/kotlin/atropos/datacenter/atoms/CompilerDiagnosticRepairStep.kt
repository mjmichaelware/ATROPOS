// AUTO-GENERATED TIER-1 ATOM: DC-TEL-136 - CompilerDiagnosticRepairStep
// HUD: #00FF9F · REWARD: +1.0 on fix / -0.5 on retry
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-136: CompilerDiagnosticRepairStep
 * Category: Repair & Synthesis
 * Expanded Context: Single-turn compiler error repair iteration, token edit radius, and AST locality.
 * Research: Research minimal surgical diffs for fast compiler error convergence.
 * Implementation: Emit +1.0 for single-step diagnostic repair; record error line and symbol locus.
 * Wire / Core Producers: CompilerDiagnosticRepair.kt, MultiLanguageDiagnosticHarvester.kt
 */
data class CompilerDiagnosticRepairStep(
    val atomId: String = "DC-TEL-136",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "+1.0 on fix / -0.5 on retry",
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
            "atom_name" to "CompilerDiagnosticRepairStep",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Single-turn compiler error repair iteration, token edit radius, and AST locality.",
            "research_line" to "Research minimal surgical diffs for fast compiler error convergence.",
            "impl_line" to "Emit +1.0 for single-step diagnostic repair; record error line and symbol locus.",
            "wire" to "CompilerDiagnosticRepair.kt, MultiLanguageDiagnosticHarvester.kt",
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
