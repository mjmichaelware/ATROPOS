/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.multimodal.InspectionService
import atropos.core.multimodal.ViewportCapture

/**
 * `/inspect` — Phase 17 drift inspection over files, the DAG, and viewports.
 *
 * Every inspection renders through [verdict] so PASS and FAIL read identically
 * across subcommands. Findings are always printed, including on a pass: an
 * inspection that passed with remarks is exactly the case where the remarks
 * matter, and a surface that hides them trains the operator to trust "PASS"
 * more than the evidence behind it.
 */
class InspectCommandHandler(
    private val inspectionService: InspectionService = InspectionService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "file" -> file(args)
        "dag" -> dag(args)
        "viewport" -> viewport(args)
        "full" -> "Full inspection: ${inspectionService.runFullInspection(args.drop(1)).summary}"
        "report" -> "Inspection report: ${inspectionService.report().summary}"
        else -> recent()
    }

    private fun file(args: List<String>): String {
        if (args.size < 2) return "usage: /inspect file <path> [ref-snapshot-id]"
        val result = inspectionService.inspectFileForDrift(args[1], args.getOrNull(2))
        return "Inspection: ${verdict(result.id, result.passed, result.findings)}"
    }

    private fun dag(args: List<String>): String {
        val expected = args.getOrNull(1)?.toIntOrNull() ?: 0
        val result = inspectionService.verifyDAGState(expected)
        return "DAG inspection: ${verdict(result.id, result.passed, result.findings)}"
    }

    private fun viewport(args: List<String>): String {
        if (args.size < 3) return "usage: /inspect viewport <content> <expected-pattern>"
        val capture = ViewportCapture(content = args[1], width = DEFAULT_WIDTH, height = DEFAULT_HEIGHT)
        val result = inspectionService.verifyViewportContent(capture, args.drop(2).joinToString(" "))
        return "Viewport inspection: ${verdict(result.id, result.passed, result.findings)}"
    }

    private fun recent(): String {
        val inspections = inspectionService.recent(RECENT_LIMIT)
        if (inspections.isEmpty()) return "no inspections recorded"
        return inspections.joinToString("\n") {
            "  ${it.id}: ${it.kind.name} ${if (it.passed) "PASS" else "FAIL"} sev=${it.severity}"
        }
    }

    private fun verdict(id: String, passed: Boolean, findings: List<String>): String =
        "$id ${if (passed) "PASS" else "FAIL"}: ${findings.joinToString("; ")}"

    private companion object {
        const val DEFAULT_WIDTH = 80
        const val DEFAULT_HEIGHT = 24
        const val RECENT_LIMIT = 5
    }
}
