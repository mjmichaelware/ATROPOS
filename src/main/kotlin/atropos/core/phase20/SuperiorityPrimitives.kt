/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

/**
 * Superiority Primitives for Phase 20 (NS01-NS07).
 */

/**
 * NS01: Proof-carrying amendment.
 * Encapsulates an amendment along with the proof and verifier's cryptographic signature.
 */
data class ProofCarryingAmendment(
    val amendmentId: String,
    val proofHash: String,
    val verifierSignature: String
)

/**
 * NS02: Formal reproducibility.
 * Calculates the reproducibility score R(d) from a set of observations.
 */
object FormalReproducibility {
    fun evaluate(observations: List<RuntimeObservation>): Double {
        if (observations.isEmpty()) return 0.0
        // A simple stand-in calculation for R(d). Real implementation would analyze timestamps/environments.
        val baseScore = observations.size.toDouble() / 10.0
        return if (baseScore > 1.0) 1.0 else baseScore
    }
}

/**
 * NS03: Metric space improvement.
 * Computes I(p) — the magnitude of improvement from baseline to observed.
 */
object MetricSpaceImprovement {
    enum class Direction { LOWER_IS_BETTER, HIGHER_IS_BETTER }

    fun computeIp(baseline: Double, observed: Double, direction: Direction): Double {
        return when (direction) {
            Direction.LOWER_IS_BETTER -> baseline - observed
            Direction.HIGHER_IS_BETTER -> observed - baseline
        }
    }
}

/**
 * NS05: Object/meta-level separation.
 * Decides if a given territory belongs to the meta-level.
 */
object ObjectMetaSeparation {
    fun isMetaLevel(territory: List<String>): Boolean {
        return territory.any { it.contains("phase20") || it.contains("meta") || it.contains("SelfImprovement") }
    }
}

/**
 * NS06: Proposal lattice.
 * Represents a dependency-ordered directed acyclic graph of proposals.
 */
data class ProposalLattice(
    val nodes: List<ImprovementProposal>
) {
    // Expected to be topologically sorted based on dependency edges
}

/**
 * NS07: Unified CAS substrate.
 * Content-addressed storage for all system state and history.
 */
interface UnifiedCasSubstrate {
    fun store(key: String, value: String)
    fun get(key: String): String?
    fun hash(value: String): String
}
