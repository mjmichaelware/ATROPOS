/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.hr.HrRouterService
import atropos.core.hr.InformationKind

/**
 * `/hr` — Phase 14 cross-territory information routing.
 *
 * Routed content is truncated in the approved case. The HR router exists so
 * that one territory's data reaches another only through a checked path, and
 * echoing an unbounded payload back to the terminal would undo that at the last
 * step — the audit log is the durable record, not the console.
 */
class HrCommandHandler(
    private val hrRouter: HrRouterService = HrRouterService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "route" -> route(args)
        "audit" -> audit()
        else -> hrRouter.auditSummary()
    }

    private fun route(args: List<String>): String {
        if (args.size < 3) return "usage: /hr route <source-owner> <dest-owner> <query>"
        val response = hrRouter.request(
            args[1],
            "terr-${args[1]}",
            args[2],
            "terr-${args[2]}",
            InformationKind.SOURCE_CODE,
            args.drop(3).joinToString(" "),
            taskId = "cli-hr-route",
            sourceCoordinates = listOf("cli:/hr/route"),
            needToKnow = "operator requested bounded redacted source context"
        )
        return if (response.approved) {
            "HR route approved: ${response.redactedContent?.take(ROUTED_CONTENT_PREVIEW)}"
        } else {
            "HR route denied: ${response.reason}"
        }
    }

    private fun audit(): String {
        val log = hrRouter.auditLog()
        if (log.isEmpty()) return "HR audit log empty"
        return log.joinToString("\n") {
            "  ${it.requestId}: ${it.sourceOwnerId}->${it.targetOwnerId} ${it.kind} " +
                "risk=${it.risk} approved=${it.approved}"
        }
    }

    private companion object {
        const val ROUTED_CONTENT_PREVIEW = 100
    }
}
