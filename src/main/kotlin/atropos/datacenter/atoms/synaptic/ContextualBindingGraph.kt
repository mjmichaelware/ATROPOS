/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.datacenter.atoms.synaptic

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * ContextualBindingGraph (DC-TEL-203)
 *
 * Maintains a sparse directed graph of SynapticEdgeAtoms connecting telemetry atoms.
 * Enforces Design Invariant 1: every admitted atom must possess at least one verified
 * synaptic edge exceeding the developmental pruning threshold.
 */
class ContextualBindingGraph(
    private var currentPhaseState: DevelopmentalPhaseState = DevelopmentalPhaseState(
        phaseId = "phase-sensorimotor-initial",
        currentPhase = DevelopmentalPhase.SENSORIMOTOR_EXPLORATORY,
        phaseProgress = 0.1,
        plasticityGain = 1.0,
        pruningThreshold = 0.15,
        criticalWindowOpen = true,
        cumulativeExperienceTokens = 100_000L
    )
) {
    private val adjacencyMap = ConcurrentHashMap<String, MutableList<SynapticEdgeAtom>>()
    private val admittedAtoms = ConcurrentHashMap.newKeySet<String>()

    fun proposeEdge(
        preAtomId: String,
        postAtomId: String,
        baseWeight: Double,
        spikeDeltaMs: Long
    ): SynapticEdgeAtom {
        val stdpMultiplier = if (spikeDeltaMs > 0) Math.exp(-spikeDeltaMs / 50.0) else 0.5
        val weight = (baseWeight * stdpMultiplier * currentPhaseState.plasticityGain).coerceIn(0.0, 1.0)

        val edge = SynapticEdgeAtom(
            edgeId = java.util.UUID.randomUUID().toString(),
            preSynapticAtomId = preAtomId,
            postSynapticAtomId = postAtomId,
            synapticWeight = weight,
            lastSpikeDeltaMs = spikeDeltaMs,
            myelinationFactor = 0.1,
            developmentalPhase = currentPhaseState.currentPhase,
            hebbianTrace = weight * 0.8,
            timestamp = Instant.now().toEpochMilli()
        )

        adjacencyMap.computeIfAbsent(preAtomId) { java.util.concurrent.CopyOnWriteArrayList() }.add(edge)
        adjacencyMap.computeIfAbsent(postAtomId) { java.util.concurrent.CopyOnWriteArrayList() }.add(edge)
        admittedAtoms.add(preAtomId)
        admittedAtoms.add(postAtomId)
        return edge
    }

    fun hasSufficientConnectivity(atomId: String): Boolean {
        val edges = adjacencyMap[atomId] ?: return false
        val threshold = currentPhaseState.pruningThreshold
        return edges.any { it.synapticWeight >= threshold }
    }

    fun pruneWeakEdges(): Int {
        val threshold = currentPhaseState.pruningThreshold
        var prunedCount = 0
        for ((_, edgeList) in adjacencyMap) {
            val iterator = edgeList.iterator()
            while (iterator.hasNext()) {
                val edge = iterator.next()
                if (edge.synapticWeight < threshold && edge.myelinationFactor < 0.5) {
                    edgeList.remove(edge)
                    prunedCount++
                }
            }
        }
        return prunedCount
    }

    fun getConnectedEdges(atomId: String): List<SynapticEdgeAtom> {
        return adjacencyMap[atomId] ?: emptyList()
    }

    companion object {
        val defaultGraph = ContextualBindingGraph()
    }
}
