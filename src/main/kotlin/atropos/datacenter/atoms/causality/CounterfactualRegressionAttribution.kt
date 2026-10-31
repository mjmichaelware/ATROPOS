/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.causality

/**
 * CounterfactualRegressionAttribution (DC-TEL-153)
 *
 * Emits formal attribution when a mutated symbol induces counterfactual regressions.
 * Tracks Pearl do-calculus proof chains and penalizes the offending causal parents.
 */
data class CounterfactualRegressionAttribution(
    val regressionId: String,
    val causalParentHash: String,
    val doCalculusProofHash: String,
    val affectedAstSymbols: List<String>,
    val backtrackedStepsCount: Int,
    val penaltyAssigned: Double
)
