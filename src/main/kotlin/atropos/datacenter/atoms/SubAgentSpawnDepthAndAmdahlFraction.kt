// AUTO-GENERATED TIER-1 ATOM: DC-TEL-031 - SubAgentSpawnDepthAndAmdahlFraction
// HUD: #64D2FF · REWARD: Depth + Amdahl
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-031: SubAgentSpawnDepthAndAmdahlFraction
 * Category: Multi-Agent / Hierarchy
 * Expanded Context: Spawn depth and measured Amdahl parallel-fraction of the resulting execution.
 * Research: Research hierarchical spawning efficiency.
 * Implementation: Emit spawn depth + Amdahl fraction.
 * Wire / Core Producers: DecoupledWorkerSynthesizer, PartitionClusterManager, IsolatedWorktreeService
 */
data class SubAgentSpawnDepthAndAmdahlFraction(
    val atomId: String = "DC-TEL-031",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Depth + Amdahl",
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
            "atom_name" to "SubAgentSpawnDepthAndAmdahlFraction",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Spawn depth and measured Amdahl parallel-fraction of the resulting execution.",
            "research_line" to "Research hierarchical spawning efficiency.",
            "impl_line" to "Emit spawn depth + Amdahl fraction.",
            "wire" to "DecoupledWorkerSynthesizer, PartitionClusterManager, IsolatedWorktreeService",
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
