// AUTO-GENERATED TIER-1 ATOM: DC-TEL-026 - DwellTimeMs
// HUD: #64D2FF · REWARD: Dwell ms (Capped)
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-026: DwellTimeMs
 * Category: Human-In-The-Loop
 * Expanded Context: Millisecond dwell time before acceptance or rejection, capped at 30 000 ms.
 * Research: Research dwell time as implicit preference strength.
 * Implementation: Emit dwell time (ms). Cap at 30 000.
 * Wire / Core Producers: BoundedRenderingController, HudRules, HumanPreferenceFeedbackCollector
 */
data class DwellTimeMs(
    val atomId: String = "DC-TEL-026",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Dwell ms (Capped)",
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
            "atom_name" to "DwellTimeMs",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Millisecond dwell time before acceptance or rejection, capped at 30 000 ms.",
            "research_line" to "Research dwell time as implicit preference strength.",
            "impl_line" to "Emit dwell time (ms). Cap at 30 000.",
            "wire" to "BoundedRenderingController, HudRules, HumanPreferenceFeedbackCollector",
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
