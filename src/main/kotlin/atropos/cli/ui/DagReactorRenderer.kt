/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.HoeStatusVocabulary
import java.util.Locale

/**
 * HOE-E03: DAG reactor presentation.
 * Nodes ignite on claim, swell with real progress, shed failures with typed reasons.
 *
 * Status arrives as a Doc 4 term rather than a private enum. This renderer used
 * to switch on names of its own, which is the drift [HoeStatusVocabulary]
 * exists to stop: a node the scheduler called `review-required` fell through to
 * the idle glyph here while every other surface showed it as needing attention.
 * The glyph and the words both come from [HoeStatusVocabulary.signal], so a
 * terminal with no colour still carries the whole signal.
 */
class DagReactorRenderer(private val theme: TerminalTheme) {
    data class ReactorNode(
        val id: String,
        /** A Doc 4 status term — see [HoeStatusVocabulary.CANONICAL_TERMS]. */
        val status: String,
        val detail: String?,
        val progress: Double = 0.0
    )

    fun render(nodes: List<ReactorNode>, width: Int, asciiOnly: Boolean = false): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        return nodes.map { node ->
            val state = HoeStatusVocabulary.resolveOrUnknown(node.status)
            val signal = HoeStatusVocabulary.signal(state, asciiOnly)

            // The colour comes from the state's own declared role rather than a
            // table here. A second table is a second answer to "what colour is
            // failed", and the two only have to disagree once.
            val icon = theme.paint(state.role, signal.icon)
            val name = node.id.take(15).padEnd(15)
            val detailText = node.detail?.let { " - $it" } ?: ""
            val pct = (node.progress * 100).toInt().toString().padStart(3) + "%"

            "$icon [${signal.text.uppercase(Locale.ROOT)}] $name $pct$detailText".take(safeWidth)
        }
    }
}
