// AUTO-GENERATED TIER-1 ATOM: DC-TEL-065 - ContainerStartupLatency
// HUD: #64D2FF · REWARD: Latency ms
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-065: ContainerStartupLatency
 * Category: Formal / Solver / AST / System
 * Expanded Context: Container startup and sandbox initialization latency.
 * Research: Research isolated execution environment overhead.
 * Implementation: Emit latency in milliseconds.
 * Wire / Core Producers: BoundedProcessRunner, DatabaseContainerMockSandboxDriver
 */
data class ContainerStartupLatency(
    val atomId: String = "DC-TEL-065",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Latency ms",
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
            "atom_name" to "ContainerStartupLatency",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Container startup and sandbox initialization latency.",
            "research_line" to "Research isolated execution environment overhead.",
            "impl_line" to "Emit latency in milliseconds.",
            "wire" to "BoundedProcessRunner, DatabaseContainerMockSandboxDriver",
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
