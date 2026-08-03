/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AppFactoryPlanRenderer

class FactoryCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: AppFactoryPlanRenderer = AppFactoryPlanRenderer(),
    private val runFactory: (String) -> String = renderer::renderRun
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderNotice(renderer.renderStatus())
            "plan" -> renderPlan(tokens.drop(2))
            "run" -> renderRun(tokens.drop(2))
            else -> uiEngine.renderError("usage: /factory [status|plan|run] <prompt>")
        }
        return RouterOutcome.CONTINUE
    }

    private fun renderPlan(parts: List<String>) {
        val prompt = parts.joinToString(" ")
        if (prompt.isBlank()) uiEngine.renderError("/factory plan requires a prompt")
        else uiEngine.renderNotice(renderer.renderPlan(prompt))
    }

    private fun renderRun(parts: List<String>) {
        val prompt = parts.joinToString(" ")
        if (prompt.isBlank()) {
            uiEngine.renderError("/factory run requires a prompt")
        } else {
            val result = try {
                runFactory(prompt)
            } catch (failure: RuntimeException) {
                uiEngine.renderError("factory run failed: ${failure.message ?: "unknown failure"}")
                return
            }
            uiEngine.renderNotice("factory run completed:")
            uiEngine.renderNotice(result)
        }
    }
}
