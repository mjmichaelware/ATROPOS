/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import atropos.core.contract.ContractLattice
import atropos.dloi.DloiLookupResult
import atropos.dloi.DloiService

class DloiDomainContractExtrapolation(private val dloiService: DloiService) {

    fun extrapolate(lattice: ContractLattice): List<String> {
        val injectedRules = mutableListOf<String>()

        // Canonical DLOI domain coordinates: 006 Security (STRIDE), 410 Data Plane, 620 Runtime, WCAG 2.2 AA
        val coordinates = listOf("006", "410", "620", "WCAG_2_2_AA", "STRIDE")

        for (coord in coordinates) {
            when (val res = dloiService.resolve(coord)) {
                is DloiLookupResult.Resolved -> {
                    injectedRules.add("require(dloi_rule_${coord}): ${res.resolution.document.id}")
                }
                is DloiLookupResult.NoMatch -> {
                    // SD7-R-080: Obey Soft-Fail Law: missing DLOI yields RecordSkip(REASON) never halting factory
                    injectedRules.add("skip(dloi_rule_${coord}): RecordSkip(${res.reason})")
                }
            }
        }

        // Schema-specific domain extrapolations
        for ((schemaName, _) in lattice.schemas) {
            val taskQuery = "security rules for $schemaName"
            val resolution = runCatching { dloiService.resolveTask(taskQuery) }.getOrNull()
            if (resolution != null) {
                injectedRules.add("require(territory_sanitization_${schemaName}): ${resolution.document.id} bound")
            } else {
                injectedRules.add("require(territory_sanitization_${schemaName}): default OWASP sanitization bound")
            }
        }

        return injectedRules
    }
}
