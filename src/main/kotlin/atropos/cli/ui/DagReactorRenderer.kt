/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.cli.ui.design.HoeStatusVocabulary

/**
 * HOE-E03: DAG reactor presentation.
 * Nodes ignite on claim, swell with real progress, shed failures with typed reasons.
 */
class DagReactorRenderer(private val theme: TerminalTheme) {
    data class ReactorNode(val id: String, val status: HoeStatusVocabulary, val detail: String?, val progress: Double = 0.0)

    fun render(nodes: List<ReactorNode>, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(40)
        return nodes.map { node ->
            val icon = when (node.status) {
                HoeStatusVocabulary.WORKING -> "⬡"
                HoeStatusVocabulary.FAILED -> "⬢"
                HoeStatusVocabulary.COMPLETED -> "⬢"
                else -> "⬡"
            }
            
            val role = when (node.status) {
                HoeStatusVocabulary.WORKING -> Role.STATUS_PENDING
                HoeStatusVocabulary.FAILED -> Role.STATUS_ERROR
                HoeStatusVocabulary.COMPLETED -> Role.STATUS_VERIFIED
                else -> Role.MUTED
            }

            val formatIcon = theme.format(icon, role)
            val name = node.id.take(15).padEnd(15)
            val detailText = node.detail?.let { " - $it" } ?: ""
            val pct = (node.progress * 100).toInt().toString().padStart(3) + "%"
            
            "$formatIcon $name $pct$detailText".take(safeWidth)
        }
    }
}
