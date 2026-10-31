/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.predictive

/**
 * PreActionSimulationVector (DC-TEL-151)
 *
 * Implements pre-execution sensory prediction and EVSI evaluation before mutating workspace code.
 * Evaluates predicted exit code, AST edit distance, entropy reduction, and expected cost.
 */
enum class PreActionGateDecision {
    DISPATCH,
    REVISE,
    PRUNE_UNPRODUCTIVE
}

data class PreActionSimulationVector(
    val simulationId: String,
    val proposalHash: String,
    val predictedExitCode: Int,
    val predictedSurprise: Double,
    val predictedAstDistance: Int,
    val evsiEstimate: Double,
    val computationCost: Long,
    val gateDecision: PreActionGateDecision
)
