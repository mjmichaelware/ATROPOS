/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import atropos.core.contract.ContractLattice
import java.security.MessageDigest

data class NegativeSpecification(
    val antiGoals: List<String>,
    val sha256: String
)

class AdversarialGoalExpansion {

    fun expand(lattice: ContractLattice): NegativeSpecification {
        val derivedAntiGoals = mutableListOf<String>()

        // Canonical negative invariants (STRIDE threat modeling)
        derivedAntiGoals.add("~G: Byzantine Fault: Unauthorized mutation or state corruption")
        derivedAntiGoals.add("~G: Resource Exhaustion: Unbounded memory allocation or thread leakage")
        derivedAntiGoals.add("~G: Network Partition: Inconsistent partition split-brain operation")

        // Invariants derived from schemas and interfaces
        for ((name, schema) in lattice.schemas) {
            derivedAntiGoals.add("~G: Schema invariant violation on entity '$name': malformed payload acceptance")
        }

        for ((ifaceName, content) in lattice.interfaceFiles) {
            val lines = content.lines()
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("fun ") || trimmed.startsWith("def ") || trimmed.startsWith("fn ")) {
                    val fnName = trimmed.substringAfter(" ").substringBefore("(").trim()
                    derivedAntiGoals.add("~G: Contract breach in '$ifaceName.$fnName': pre-condition bypass or arithmetic overflow")
                }
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        for (goal in derivedAntiGoals) {
            digest.update(goal.toByteArray(Charsets.UTF_8))
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }

        return NegativeSpecification(
            antiGoals = derivedAntiGoals,
            sha256 = hash
        )
    }
}
