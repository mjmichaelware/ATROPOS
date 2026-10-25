// AUTO-GENERATED TIER-1 ATOM: DC-TEL-141 - MemoryCompactionDelta
// HUD: #FFD60A · REWARD: Compaction Ratio
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-141: MemoryCompactionDelta
 * Category: Memory Management
 * Expanded Context: Token and byte delta after episodic memory compaction pass across long horizons.
 * Research: Research memory distillation for multi-day autonomous agent continuity.
 * Implementation: Emit raw bytes, compacted bytes, and information retention ratio.
 * Wire / Core Producers: LocalMemoryStore.kt, CodebaseContextPacker.kt
 */
data class MemoryCompactionDelta(
    val atomId: String = "DC-TEL-141",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Compaction Ratio",
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
            "atom_name" to "MemoryCompactionDelta",
            "hud_color" to "#FFD60A",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Token and byte delta after episodic memory compaction pass across long horizons.",
            "research_line" to "Research memory distillation for multi-day autonomous agent continuity.",
            "impl_line" to "Emit raw bytes, compacted bytes, and information retention ratio.",
            "wire" to "LocalMemoryStore.kt, CodebaseContextPacker.kt",
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
