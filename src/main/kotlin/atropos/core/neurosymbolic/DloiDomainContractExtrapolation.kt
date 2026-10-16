/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import atropos.core.contract.ContractLattice
import atropos.dloi.DloiService

class DloiDomainContractExtrapolation(private val dloiService: DloiService) {

    fun extrapolate(lattice: ContractLattice): List<String> {
        val injectedRules = mutableListOf<String>()

        // Canonical DLOI domain coordinates: 006 Security, 410 Data Plane, 620 Runtime
        val coordinates = listOf("006", "410", "620")

        for (coord in coordinates) {
            val res = dloiService.lookup(coord)
            if (res.found) {
                for (doc in res.documents) {
                    injectedRules.add("require(dloi_rule_${coord}): ${doc.title}")
                }
            }
        }

        // Schema-specific domain extrapolations
        for ((schemaName, schemaContent) in lattice.schemas) {
            val taskQuery = "security rules for $schemaName"
            val resolution = dloiService.resolveTask(taskQuery)
            if (resolution.found) {
                injectedRules.add("require(territory_sanitization_${schemaName}): ${resolution.resolvedLines.size} invariants bound")
            } else {
                injectedRules.add("require(territory_sanitization_${schemaName}): default OWASP sanitization bound")
            }
        }

        return injectedRules
    }
}
