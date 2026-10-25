// AUTO-GENERATED TIER-1 ATOM: DC-TEL-033 - RoleConflictOrAuthorityViolation
// HUD: #FF3B30 · REWARD: -10.0 Hard
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-033: RoleConflictOrAuthorityViolation
 * Category: Multi-Agent / Hierarchy
 * Expanded Context: Any attempt by a worker to write outside its granted territory.
 * Research: Research authority violation as hard fraud signal.
 * Implementation: Emit violation + -10.0 fraud penalty if confirmed.
 * Wire / Core Producers: TerritoryGrantService, TargetWorkspaceLocator.kt, AntiGamingAuditor
 */
data class RoleConflictOrAuthorityViolation(
    val atomId: String = "DC-TEL-033",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "-10.0 Hard",
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
            "atom_name" to "RoleConflictOrAuthorityViolation",
            "hud_color" to "#FF3B30",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Any attempt by a worker to write outside its granted territory.",
            "research_line" to "Research authority violation as hard fraud signal.",
            "impl_line" to "Emit violation + -10.0 fraud penalty if confirmed.",
            "wire" to "TerritoryGrantService, TargetWorkspaceLocator.kt, AntiGamingAuditor",
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
