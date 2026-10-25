// AUTO-GENERATED TIER-1 ATOM: DC-TEL-071 - CpuThreadUtilization
// HUD: #64D2FF · REWARD: Utilization %
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-071: CpuThreadUtilization
 * Category: Formal / Solver / AST / System
 * Expanded Context: CPU thread utilization and core occupancy during builds/tests.
 * Research: Research parallel task scheduling efficiency.
 * Implementation: Emit utilization percentage vector across cores.
 * Wire / Core Producers: HardwareProfileAdapter, PlatformAdapterRegistry
 */
data class CpuThreadUtilization(
    val atomId: String = "DC-TEL-071",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Utilization %",
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
            "atom_name" to "CpuThreadUtilization",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "CPU thread utilization and core occupancy during builds/tests.",
            "research_line" to "Research parallel task scheduling efficiency.",
            "impl_line" to "Emit utilization percentage vector across cores.",
            "wire" to "HardwareProfileAdapter, PlatformAdapterRegistry",
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
