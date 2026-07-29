/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.ast.AstSymbolGraph
import atropos.cli.ui.AnsiTerminalEngine

class AstCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val graph: AstSymbolGraph = AstSymbolGraph()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            "lookup" -> lookup(tokens)
            else -> uiEngine.renderError("usage: /ast lookup <symbol>")
        }
        return RouterOutcome.CONTINUE
    }

    private fun lookup(tokens: List<String>) {
        val query = tokens.drop(2).joinToString(" ").trim()
        if (query.isBlank()) {
            uiEngine.renderError("usage: /ast lookup <symbol>")
        } else {
            uiEngine.renderNotice(graph.lookup(query).render())
        }
    }
}
