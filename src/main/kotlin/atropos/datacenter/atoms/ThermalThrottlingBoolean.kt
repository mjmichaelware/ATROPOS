// AUTO-GENERATED TIER-1 ATOM: DC-TEL-072 - ThermalThrottlingBoolean
// HUD: #FF9500 · REWARD: Thermal Status
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-072: ThermalThrottlingBoolean
 * Category: Formal / Solver / AST / System
 * Expanded Context: Thermal throttling status via PlatformWire / system sensors.
 * Research: Research mobile / edge device compute stability.
 * Implementation: Emit boolean throttling flag.
 * Wire / Core Producers: PlatformWire, HardwareProfileAdapter
 */
data class ThermalThrottlingBoolean(
    val atomId: String = "DC-TEL-072",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Thermal Status",
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
            "atom_name" to "ThermalThrottlingBoolean",
            "hud_color" to "#FF9500",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Thermal throttling status via PlatformWire / system sensors.",
            "research_line" to "Research mobile / edge device compute stability.",
            "impl_line" to "Emit boolean throttling flag.",
            "wire" to "PlatformWire, HardwareProfileAdapter",
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
