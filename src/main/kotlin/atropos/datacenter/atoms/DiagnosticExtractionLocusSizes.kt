// AUTO-GENERATED TIER-1 ATOM: DC-TEL-123 - DiagnosticExtractionLocusSizes
// HUD: #64D2FF · REWARD: Slice Size Lines
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-123: DiagnosticExtractionLocusSizes
 * Category: Formal / Solver / AST / System
 * Expanded Context: Diagnostic extraction locus sizes (+/-25 line slices around errors).
 * Research: Research minimal context extraction for compiler error repair.
 * Implementation: Emit extracted slice line count and token size.
 * Wire / Core Producers: MultiLanguageDiagnosticHarvester, DiagnosticSymbolSlicer
 */
data class DiagnosticExtractionLocusSizes(
    val atomId: String = "DC-TEL-123",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Slice Size Lines",
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
            "atom_name" to "DiagnosticExtractionLocusSizes",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Diagnostic extraction locus sizes (+/-25 line slices around errors).",
            "research_line" to "Research minimal context extraction for compiler error repair.",
            "impl_line" to "Emit extracted slice line count and token size.",
            "wire" to "MultiLanguageDiagnosticHarvester, DiagnosticSymbolSlicer",
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
