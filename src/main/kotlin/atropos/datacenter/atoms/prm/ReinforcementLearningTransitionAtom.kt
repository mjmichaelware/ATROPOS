/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.prm

/**
 * ReinforcementLearningTransitionAtom
 *
 * Implements Markov Decision Process (MDP) transition telemetry.
 * Strictly separates `terminated` (absorbing final state, e.g. 0 compile errors)
 * from `truncated` (budget or time limit reached) to prevent value function collapse.
 * Serializes GAE advantages and logprob baselines for off-policy PPO/CQL training.
 */
data class ReinforcementLearningTransitionAtom(
    val transitionId: String,
    val trajectoryId: String,
    val stepIndex: Int,
    val observationHash: String,
    val actionHash: String,
    val scalarReward: Double,
    val logprob: Double,                    // ln pi_theta(a | s)
    val refLogprob: Double,                 // ln pi_ref(a | s)
    val valueEstimate: Double,              // V(s)
    val generalizedAdvantage: Double,       // GAE A_hat
    val terminated: Boolean,
    val truncated: Boolean,
    val timestampEpochMs: Long
)
