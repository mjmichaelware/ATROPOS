/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusSecurityRenderer

class SecurityCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: StatusSecurityRenderer = StatusSecurityRenderer()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            "redact" -> uiEngine.renderNotice(renderer.renderRedaction(tokens.drop(2).joinToString(" ")))
            null, "status" -> uiEngine.renderNotice(renderer.render())
            else -> uiEngine.renderError("usage: /security [status|redact <text>]")
        }
        return RouterOutcome.CONTINUE
    }
}
