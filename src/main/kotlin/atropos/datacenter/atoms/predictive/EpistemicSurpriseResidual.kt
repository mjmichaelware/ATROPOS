/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.predictive

/**
 * EpistemicSurpriseResidual (DC-TEL-152)
 *
 * Captures prediction error between simulated virtual sensory outcomes and physical
 * compiler diagnostics. Directly drives neurodevelopmental synaptic weight adjustments.
 */
data class EpistemicSurpriseResidual(
    val residualId: String,
    val simulationId: String,
    val actualDiagnosticHash: String,
    val predictedDiagnosticHash: String,
    val klDivergenceScore: Double,
    val plasticityUpdateDelta: Double,
    val timestamp: Long
)
