/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.ast.AstSymbolGraph
import atropos.cli.ui.AnsiTerminalEngine

class AstCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val graph: AstSymbolGraph = AstSymbolGraph()
) {
    private val theme = atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())
    private val surface get() = theme.surface

    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            "lookup" -> lookup(tokens)
            "callers" -> callers(tokens)
            else -> uiEngine.renderError("usage: /ast lookup <symbol> OR /ast callers <symbol>")
        }
        return RouterOutcome.CONTINUE
    }

    private fun lookup(tokens: List<String>) {
        val query = tokens.drop(2).joinToString(" ").trim()
        if (query.isBlank()) {
            uiEngine.renderError("usage: /ast lookup <symbol>")
        } else {
            val result = graph.lookup(query)
            val body = result.render().lines().map { "  $it" }
            uiEngine.renderBlock(surface.block("AST LOOKUP: $query", body, uiEngine.viewportWidth, atropos.cli.ui.design.Role.BRAND))
        }
    }

    private fun callers(tokens: List<String>) {
        val query = tokens.drop(2).joinToString(" ").trim()
        if (query.isBlank()) {
            uiEngine.renderError("usage: /ast callers <symbol>")
        } else {
            val callers = graph.findCallers(query)
            if (callers.isEmpty()) {
                uiEngine.renderBlock(
                    surface.block(
                        "AST CALLERS: $query",
                        listOf(surface.hint("no callers found", uiEngine.viewportWidth)),
                        uiEngine.viewportWidth,
                        atropos.cli.ui.design.Role.BRAND
                    )
                )
            } else {
                val body = callers.map { caller ->
                    surface.statusRow(caller.qualifiedName, "file=${caller.file.fileName}", atropos.cli.ui.design.Health.VERIFIED, uiEngine.viewportWidth)
                }
                uiEngine.renderBlock(surface.block("AST CALLERS: $query", body, uiEngine.viewportWidth, atropos.cli.ui.design.Role.BRAND))
            }
        }
    }
}
