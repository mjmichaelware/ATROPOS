/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.prm

/**
 * ProcessSupervisionStepAtom
 *
 * Implements per-step PRM (Process-Supervised Reward Model) annotation.
 * Encapsulates Monte Carlo rollout estimates, SMT equivalence witness hashes,
 * and binary search error localization flags.
 */
data class ProcessSupervisionStepAtom(
    val stepId: String,
    val trajectoryId: String,
    val stepIndex: Int,
    val prefixHash: String,
    val stepContent: String,
    val rawMonteCarloProbability: Double, // y_k from M rollouts
    val pointwiseSoftLabel: Double,       // Sigmoid-calibrated target
    val firstErrorStep: Boolean,          // Binary search error locator flag
    val smtEquivalenceWitness: String?    // Null if unverified, proof hash if sound
)
