/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.dloi.DloiLookupResult
import atropos.dloi.HigZeroGuard

class DloiCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val higZeroGuard: HigZeroGuard
) {
    fun execute(tokens: List<String>) {
        when (tokens.getOrNull(1)?.lowercase()) {
            "lookup" -> {
                val address = tokens.drop(2).joinToString(" ").trim()
                if (address.isBlank()) {
                    uiEngine.renderError("usage: /dloi lookup <document#section@Lstart[-end]>")
                } else {
                    uiEngine.renderNotice(render(higZeroGuard.resolve(address), address))
                }
            }
            "resolve" -> {
                val task = tokens.drop(2).joinToString(" ").trim()
                if (task.isBlank()) {
                    uiEngine.renderError("usage: /dloi resolve <task text>")
                } else {
                    uiEngine.renderNotice(render(higZeroGuard.resolveTask(task), task))
                }
            }
            else -> uiEngine.renderError("usage: /dloi [lookup <address>|resolve <task>]")
        }
    }

    private fun render(result: DloiLookupResult, query: String): String = when (result) {
        is DloiLookupResult.Resolved -> result.resolution.render()
        is DloiLookupResult.NoMatch ->
            "dloi: no exact match for '${query.trim()}'\n  reason: ${result.reason}\n  no nearest-match substitute is offered"
    }
}
