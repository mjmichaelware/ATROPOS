// AUTO-GENERATED TIER-1 ATOM: DC-TEL-020 - CacheHitAttribution
// HUD: #00FF9F · REWARD: Hit Map + Savings
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-020: CacheHitAttribution
 * Category: Token / Context / Efficiency
 * Expanded Context: Which tokens were served from cache and the resulting cost saving.
 * Research: Research cache efficiency attribution.
 * Implementation: Emit cache-hit map + cost saving.
 * Wire / Core Producers: ProviderHttpClient.kt, ProviderState.kt
 */
data class CacheHitAttribution(
    val atomId: String = "DC-TEL-020",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Hit Map + Savings",
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
            "atom_name" to "CacheHitAttribution",
            "hud_color" to "#00FF9F",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Which tokens were served from cache and the resulting cost saving.",
            "research_line" to "Research cache efficiency attribution.",
            "impl_line" to "Emit cache-hit map + cost saving.",
            "wire" to "ProviderHttpClient.kt, ProviderState.kt",
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
