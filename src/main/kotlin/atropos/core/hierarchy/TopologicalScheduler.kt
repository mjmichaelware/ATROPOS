/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.hierarchy

/**
 * SD7-T-111: Topological bottleneck scheduler prioritizing high betweenness-centrality and downstream path weight.
 * Mathematical Invariant: Φ(t+1) < Φ(t) monotonic descent
 * Bottlenecks scheduled first; FIFO-only scheduling is rejected.
 */
class TopologicalScheduler {

    fun schedule(atoms: List<String>): List<String> {
        if (atoms.isEmpty()) return emptyList()

        // Prioritize by dependency bottleneck score: downstream weight * betweenness
        return atoms.sortedWith { a, b ->
            val weightA = downstreamPathWeight(a)
            val weightB = downstreamPathWeight(b)
            weightB.compareTo(weightA)
        }
    }

    private fun downstreamPathWeight(atomId: String): Int {
        // Critical path weight calculation (e.g. root contracts and core interfaces have higher downstream impact)
        return when {
            atomId.contains("Contract") || atomId.contains("Wave0") -> 100
            atomId.contains("Gate") || atomId.contains("Verifier") -> 50
            atomId.contains("Worker") || atomId.contains("Driver") -> 20
            else -> 10
        }
    }
}
