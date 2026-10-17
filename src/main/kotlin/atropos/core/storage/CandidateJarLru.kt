/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.util.LinkedHashMap

/**
 * SD7-T-119: Candidate JAR lifecycle tracker and diagnostic-hash LRU cache.
 * Mathematical Invariant: Exact-hash restart continuity
 * Blocks oscillation by detecting repeated diagnostic hashes and escalating to HypothesisDisputationEngine.
 */
class CandidateJarLru(
    private val maxEntries: Int = 100
) {
    private val candidateJars = mutableSetOf<String>()
    private val diagnosticHashCounts = object : LinkedHashMap<String, Int>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Int>?): Boolean {
            return size > maxEntries
        }
    }

    fun trackJar(hash: String) {
        candidateJars.add(hash)
    }

    fun recordDiagnosticHash(diagHash: String): Boolean {
        val count = diagnosticHashCounts.getOrDefault(diagHash, 0) + 1
        diagnosticHashCounts[diagHash] = count
        // Repeated identical diagnostic hash triggers hypothesis disputation
        return count >= 2
    }

    fun gcCandidate(hash: String) {
        candidateJars.remove(hash)
    }
}
