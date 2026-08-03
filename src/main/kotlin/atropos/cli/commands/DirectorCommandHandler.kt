/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.director.DirectorService
import atropos.core.director.DriftSeverity
import atropos.core.director.ObservationKind
import atropos.core.territory.TerritoryService

/**
 * `/director` — Phase 12 advisory observations.
 *
 * One of twelve subsystem surfaces that used to share a single command class.
 * Each now owns its own file so that a change to the director's vocabulary
 * touches the director's file and nothing else; previously every subsystem's
 * CLI shared one blast radius.
 *
 * The handler renders text and calls the service. It decides nothing about
 * drift itself — [DirectorService] owns that, and this is the surface over it.
 */
class DirectorCommandHandler(
    private val directorService: DirectorService = DirectorService(),
    private val territoryService: TerritoryService = TerritoryService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "observe" -> observe(args)
        "report" -> report()
        "acknowledge" -> acknowledge(args)
        "dismiss" -> dismiss(args)
        "scan" -> scan()
        else -> "director subcommand required: observe, report, acknowledge, dismiss, scan"
    }

    private fun observe(args: List<String>): String {
        if (args.size < 4) return "usage: /director observe <kind> <severity> <source> <details>"

        // Both enums are parsed before anything is recorded. A half-valid
        // observation is worse than a refused one: it would enter the advisory
        // record under a severity nobody chose.
        val kind = runCatching { ObservationKind.valueOf(args[1].uppercase()) }.getOrNull()
            ?: return "unknown kind: ${args[1]}; valid: ${ObservationKind.entries.joinToString(", ")}"
        val severity = runCatching { DriftSeverity.valueOf(args[2].uppercase()) }.getOrNull()
            ?: return "unknown severity: ${args[2]}; valid: ${DriftSeverity.entries.joinToString(", ")}"

        val observation = directorService.observe(kind, severity, args[3], args.drop(4).joinToString(" "))
        return "observation recorded: ${observation.id} (${observation.kind.name}/${observation.severity.name})"
    }

    private fun report(): String {
        val report = directorService.advisoryReport()
        return buildString {
            appendLine("Director Advisory Report: ${report.summary}")
            report.observations.forEach { observation ->
                appendLine("  [${observation.severity.name}] ${observation.id}: ${observation.details} (${observation.source})")
            }
        }.trimEnd()
    }

    private fun acknowledge(args: List<String>): String {
        if (args.size < 2) return "usage: /director acknowledge <id>"
        return if (directorService.acknowledge(args[1])) {
            "observation ${args[1]} acknowledged"
        } else {
            "observation not found: ${args[1]}"
        }
    }

    private fun dismiss(args: List<String>): String {
        if (args.size < 2) return "usage: /director dismiss <id>"
        return if (directorService.dismiss(args[1])) {
            "observation ${args[1]} dismissed"
        } else {
            "observation not found: ${args[1]}"
        }
    }

    private fun scan(): String {
        val observations = directorService.scanDiffForDrift(territoryService.getAll())
        if (observations.isEmpty()) return "no drift or violations detected"
        return observations.joinToString("\n") { "  [${it.severity.name}] ${it.details}" }
    }
}
