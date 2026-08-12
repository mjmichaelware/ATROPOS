/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/** Keeps evidence in the same result region while changing disclosure depth. */
class EvidenceMorph {
    enum class Surface { CARD, DRAWER }
    data class View(val surface: Surface, val text: String, val expanded: Boolean)

    fun morph(summary: String, evidence: String?, expanded: Boolean, width: Int): View {
        val cleanSummary = TerminalText.sanitize(summary)
        val body = if (expanded && !evidence.isNullOrBlank()) {
            "$cleanSummary\n${TerminalText.sanitize(evidence)}"
        } else {
            cleanSummary
        }
        return View(
            surface = if (expanded && !evidence.isNullOrBlank()) Surface.DRAWER else Surface.CARD,
            text = body.lines().joinToString("\n") { TerminalText.ellipsize(it, width.coerceAtLeast(1)) },
            expanded = expanded && !evidence.isNullOrBlank()
        )
    }
}
