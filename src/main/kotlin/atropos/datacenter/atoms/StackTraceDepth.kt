// AUTO-GENERATED TIER-1 ATOM: DC-TEL-085 - StackTraceDepth
// HUD: #64D2FF · REWARD: Depth Frames
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-085: StackTraceDepth
 * Category: Formal / Solver / AST / System
 * Expanded Context: Stack-trace depth and root cause exception locus.
 * Research: Research exception propagation and error locality.
 * Implementation: Emit stack frame depth integer.
 * Wire / Core Producers: MultiLanguageDiagnosticHarvester, SentryCrashReproduction
 */
data class StackTraceDepth(
    val atomId: String = "DC-TEL-085",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Depth Frames",
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
            "atom_name" to "StackTraceDepth",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Stack-trace depth and root cause exception locus.",
            "research_line" to "Research exception propagation and error locality.",
            "impl_line" to "Emit stack frame depth integer.",
            "wire" to "MultiLanguageDiagnosticHarvester, SentryCrashReproduction",
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
