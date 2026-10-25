// AUTO-GENERATED TIER-1 ATOM: DC-TEL-019 - TtftAndInterTokenLatency
// HUD: #64D2FF · REWARD: Latency Vectors
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-019: TtftAndInterTokenLatency
 * Category: Token / Context / Efficiency
 * Expanded Context: Time-to-first-token and inter-token latency distributions per model call.
 * Research: Research latency as a process-supervision signal.
 * Implementation: Emit TTFT and inter-token latency vectors.
 * Wire / Core Producers: ProviderHttpClient.kt, BaseHttpProvider, ProviderCascadeRouter
 */
data class TtftAndInterTokenLatency(
    val atomId: String = "DC-TEL-019",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Latency Vectors",
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
            "atom_name" to "TtftAndInterTokenLatency",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Time-to-first-token and inter-token latency distributions per model call.",
            "research_line" to "Research latency as a process-supervision signal.",
            "impl_line" to "Emit TTFT and inter-token latency vectors.",
            "wire" to "ProviderHttpClient.kt, BaseHttpProvider, ProviderCascadeRouter",
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
