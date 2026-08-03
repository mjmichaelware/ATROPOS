/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.auditor.AuditorService
import atropos.core.territory.TerritoryService

/**
 * `/auditor` — Phase 15 territory audit.
 *
 * `run` audits and then reports; the bare command reports what the last run
 * found. Keeping the two distinct matters because reading a stale report is a
 * legitimate thing to want, and making every look-up re-audit would mean the
 * operator can never see what a previous run concluded.
 */
class AuditorCommandHandler(
    private val auditor: AuditorService = AuditorService(),
    private val territoryService: TerritoryService = TerritoryService()
) {
    fun handle(args: List<String>): String {
        if (args.firstOrNull() == "run") {
            auditor.auditTerritories(territoryService.getAll())
        }
        return "Auditor: ${auditor.report().summary}"
    }
}
