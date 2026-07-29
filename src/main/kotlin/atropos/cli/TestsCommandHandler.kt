/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.TestMatrixRenderer

class TestsCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: TestMatrixRenderer = TestMatrixRenderer()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "matrix" -> uiEngine.renderNotice(renderer.render())
            else -> uiEngine.renderError("usage: /tests matrix")
        }
        return RouterOutcome.CONTINUE
    }
}
