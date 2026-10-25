// AUTO-GENERATED TIER-1 ATOM: DC-TEL-048 - ImportGraphTopologicalChanges
// HUD: #64D2FF · REWARD: Topo Delta
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-048: ImportGraphTopologicalChanges
 * Category: Formal / Solver / AST / System
 * Expanded Context: Topological changes to the import graph after mutation.
 * Research: Research dependency lattice shifts during refactorings.
 * Implementation: Emit topological sort delta and new directed edges.
 * Wire / Core Producers: TreeSitterGrammarBridge, CodebaseDeltaTreeTracker
 */
data class ImportGraphTopologicalChanges(
    val atomId: String = "DC-TEL-048",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Topo Delta",
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
            "atom_name" to "ImportGraphTopologicalChanges",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Topological changes to the import graph after mutation.",
            "research_line" to "Research dependency lattice shifts during refactorings.",
            "impl_line" to "Emit topological sort delta and new directed edges.",
            "wire" to "TreeSitterGrammarBridge, CodebaseDeltaTreeTracker",
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
