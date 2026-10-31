/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.synaptic

/**
 * SynapticEdgeAtom (DC-TEL-201)
 *
 * Implements Phase 21 Neurodevelopmental Connectivity Substrate.
 * Binds pairs of telemetry atoms with normalized synaptic weights, STDP spike timings,
 * myelination factors, and Hebbian eligibility traces.
 */
data class SynapticEdgeAtom(
    val edgeId: String,
    val preSynapticAtomId: String,
    val postSynapticAtomId: String,
    val synapticWeight: Double,
    val lastSpikeDeltaMs: Long,
    val myelinationFactor: Double,
    val developmentalPhase: DevelopmentalPhase,
    val hebbianTrace: Double,
    val timestamp: Long
)
