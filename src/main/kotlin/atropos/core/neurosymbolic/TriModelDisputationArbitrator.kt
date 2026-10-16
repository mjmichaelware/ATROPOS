/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import java.nio.file.Path

class TriModelDisputationArbitrator(private val prover: SmtEquivalenceProver = SmtEquivalenceProver()) {

    fun arbitrate(candidates: List<String>): String {
        require(candidates.isNotEmpty()) { "Candidates list cannot be empty" }
        if (candidates.size == 1) return candidates.first()

        // 1. Check syntactic/semantic identity
        val distinct = candidates.distinct()
        if (distinct.size == 1) {
            return distinct.first()
        }

        // 2. Rank 2: SMT verification of candidates
        val verifiedCandidates = mutableListOf<String>()
        for (candidate in distinct) {
            val proof = prover.verifySmtLibConstraint(candidate)
            if (proof.discharged) {
                verifiedCandidates.add(candidate)
            }
        }

        // 3. Minimum Description Length (MDL) selection among sound candidates
        val pool = if (verifiedCandidates.isNotEmpty()) verifiedCandidates else distinct
        return pool.minByOrNull { it.length } ?: candidates.first()
    }
}
