/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.territory.TerritoryService

/**
 * `/territory` — Phase 13 assignment, revocation, and violation review.
 *
 * Listing is the bare-command default rather than an error. Territory is the
 * thing an operator most often wants to simply see, and making them remember a
 * subcommand to look at it discourages the check.
 */
class TerritoryCommandHandler(
    private val territoryService: TerritoryService = TerritoryService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "assign" -> assign(args)
        "revoke" -> revoke(args)
        "violations" -> violations()
        "resolve" -> resolve(args)
        else -> list()
    }

    private fun assign(args: List<String>): String {
        // Four tokens are required — the subcommand plus owner, role, prefix —
        // and args[3] is read below, so a size-3 list would throw.
        if (args.size < 4) return "usage: /territory assign <owner> <role> <prefix>"
        val assignment = territoryService.assign(args[1], args[2], args[3])
        return "territory assigned: ${assignment.id} owner=${assignment.ownerId} " +
            "role=${assignment.ownerRole} prefix=${assignment.allowedPrefix}"
    }

    private fun revoke(args: List<String>): String {
        if (args.size < 2) return "usage: /territory revoke <id>"
        territoryService.revoke(args[1])
        return "territory ${args[1]} revoked"
    }

    private fun violations(): String {
        val violations = territoryService.getViolations()
        if (violations.isEmpty()) return "no territory violations"
        return violations.joinToString("\n") {
            "  ${it.id}: ${it.filePath} - ${it.reason} (resolved=${it.resolved})"
        }
    }

    private fun resolve(args: List<String>): String {
        if (args.size < 2) return "usage: /territory resolve <violation-id>"
        territoryService.resolveViolation(args[1])
        return "violation ${args[1]} resolved"
    }

    private fun list(): String {
        val assignments = territoryService.getAll()
        if (assignments.isEmpty()) return "no territory assignments"
        return assignments.joinToString("\n") {
            "  ${it.id}: ${it.ownerId} (${it.ownerRole}) -> ${it.allowedPrefix}"
        }
    }
}
