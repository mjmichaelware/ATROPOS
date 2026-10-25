// AUTO-GENERATED TIER-1 ATOM: DC-TEL-030 - AuthorityTerritoryLeaseEvents
// HUD: #BF5AF2 · REWARD: Lease Events
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-030: AuthorityTerritoryLeaseEvents
 * Category: Multi-Agent / Hierarchy
 * Expanded Context: Grant, revocation, and lease-expiration events with timestamps and territory path prefixes.
 * Research: Research authority / territory lease management.
 * Implementation: Emit lease events with timestamps and path prefixes.
 * Wire / Core Producers: TerritoryGrantService, HierarchyRegistry, TerritoryGrant.kt
 */
data class AuthorityTerritoryLeaseEvents(
    val atomId: String = "DC-TEL-030",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Lease Events",
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
            "atom_name" to "AuthorityTerritoryLeaseEvents",
            "hud_color" to "#BF5AF2",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Grant, revocation, and lease-expiration events with timestamps and territory path prefixes.",
            "research_line" to "Research authority / territory lease management.",
            "impl_line" to "Emit lease events with timestamps and path prefixes.",
            "wire" to "TerritoryGrantService, HierarchyRegistry, TerritoryGrant.kt",
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
