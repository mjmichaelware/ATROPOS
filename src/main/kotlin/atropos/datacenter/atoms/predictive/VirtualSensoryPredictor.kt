/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.predictive

import java.time.Instant

/**
 * VirtualSensoryPredictor
 *
 * Implements Active Inference Forward Simulator.
 * Simulates proposed AST mutations in a virtual cognitive sandbox prior to disk emission,
 * computing EVSI (Expected Value of Sample Information) and pruning zero-entropy actions.
 */
data class CandidateMutationProposal(
    val proposalId: String,
    val targetFile: String,
    val originalAstSignature: String,
    val proposedAstSignature: String,
    val tokenCostEstimate: Long
)

class VirtualSensoryPredictor(
    private val evsiThreshold: Double = 0.15
) {
    fun simulateProposal(proposal: CandidateMutationProposal): PreActionSimulationVector {
        val distance = computeTreeEditDistance(proposal.originalAstSignature, proposal.proposedAstSignature)
        val predictedSurprise = (distance * 0.05).coerceIn(0.0, 1.0)
        val predictedExitCode = if (predictedSurprise > 0.8) 1 else 0
        val informationGain = if (distance > 0) Math.log(1.0 + distance) * 0.25 else 0.0
        val cost = proposal.tokenCostEstimate.coerceAtLeast(10L)
        val evsi = informationGain / (cost * 0.001)

        val decision = when {
            evsi >= evsiThreshold && predictedExitCode == 0 -> PreActionGateDecision.DISPATCH
            evsi >= evsiThreshold -> PreActionGateDecision.REVISE
            else -> PreActionGateDecision.PRUNE_UNPRODUCTIVE
        }

        return PreActionSimulationVector(
            simulationId = java.util.UUID.randomUUID().toString(),
            proposalHash = Integer.toHexString(proposal.proposedAstSignature.hashCode()),
            predictedExitCode = predictedExitCode,
            predictedSurprise = predictedSurprise,
            predictedAstDistance = distance,
            evsiEstimate = evsi,
            computationCost = cost,
            gateDecision = decision
        )
    }

    fun measureSurpriseResidual(
        simulation: PreActionSimulationVector,
        actualDiagnostic: String
    ): EpistemicSurpriseResidual {
        val actualHash = Integer.toHexString(actualDiagnostic.hashCode())
        val isMismatch = simulation.proposalHash != actualHash
        val klScore = if (isMismatch) 0.65 else 0.05
        val plasticity = klScore * (1.0 / (1.0 + simulation.predictedAstDistance))

        return EpistemicSurpriseResidual(
            residualId = java.util.UUID.randomUUID().toString(),
            simulationId = simulation.simulationId,
            actualDiagnosticHash = actualHash,
            predictedDiagnosticHash = simulation.proposalHash,
            klDivergenceScore = klScore,
            plasticityUpdateDelta = plasticity,
            timestamp = Instant.now().toEpochMilli()
        )
    }

    private fun computeTreeEditDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        var diff = Math.abs(s1.length - s2.length)
        val minLen = Math.min(s1.length, s2.length)
        for (i in 0 until minLen) {
            if (s1[i] != s2[i]) diff++
        }
        return diff
    }

    companion object {
        val defaultPredictor = VirtualSensoryPredictor()
    }
}
