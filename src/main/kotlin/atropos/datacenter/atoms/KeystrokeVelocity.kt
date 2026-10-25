// AUTO-GENERATED TIER-1 ATOM: DC-TEL-092 - KeystrokeVelocity
// HUD: #64D2FF · REWARD: Velocity chars/s
// CONTRACT: Single-responsibility, zero side effects, Parquet/HF serialization compliant.

package atropos.datacenter.atoms

import java.time.Instant

/**
 * DC-TEL-092: KeystrokeVelocity
 * Category: Formal / Solver / AST / System
 * Expanded Context: Operator keystroke velocity and burst cadence.
 * Research: Research human typing patterns during code review.
 * Implementation: Emit velocity in chars/second.
 * Wire / Core Producers: BoundedRenderingController, HumanPreferenceFeedbackCollector
 */
data class KeystrokeVelocity(
    val atomId: String = "DC-TEL-092",
    val timestamp: Instant = Instant.now(),
    val payload: Map<String, String> = emptyMap(),
    val diagnosticHash: String? = null,
    val rewardValue: String = "Velocity chars/s",
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
            "atom_name" to "KeystrokeVelocity",
            "hud_color" to "#64D2FF",
            "timestamp" to timestamp.toString(),
            "reward" to rewardValue,
            "context" to "Operator keystroke velocity and burst cadence.",
            "research_line" to "Research human typing patterns during code review.",
            "impl_line" to "Emit velocity in chars/second.",
            "wire" to "BoundedRenderingController, HumanPreferenceFeedbackCollector",
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
