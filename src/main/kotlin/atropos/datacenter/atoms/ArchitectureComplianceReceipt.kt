// AUTO-GENERATED TIER-1 ATOM: DC-TEL-140 - ArchitectureComplianceReceipt
// HUD: #00FF9F · REWARD: +0.5 Compliance
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-140: ArchitectureComplianceReceipt
 * Category: Architecture Governance
 * Expanded Context: Module boundary, package containment, and clean architecture compliance verification receipt.
 * Research: Research automated architectural constraint enforcement in LLM code generation.
 * Implementation: Emit boundary check status and dependency graph compliance score.
 * Wire / Core Producers: ArchitectureComplianceChecker.kt, TreeSitterGrammarBridge.kt
 */
data class ArchitectureComplianceReceipt(
    val atomId: String = "DC-TEL-140",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "+0.5 Compliance",
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
            "atom_name" to "ArchitectureComplianceReceipt",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Module boundary, package containment, and clean architecture compliance verification receipt.",
            "research_line" to "Research automated architectural constraint enforcement in LLM code generation.",
            "impl_line" to "Emit boundary check status and dependency graph compliance score.",
            "wire" to "ArchitectureComplianceChecker.kt, TreeSitterGrammarBridge.kt",
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
