/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusCiRenderer
import atropos.core.execution.LocalWorkQueue

class CiCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val queue: LocalWorkQueue = LocalWorkQueue(),
    private val renderer: StatusCiRenderer = StatusCiRenderer()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.drop(1).joinToString(" ").lowercase()) {
            "local compile" -> uiEngine.renderNotice("queued local compile: ${queue.enqueueLocalCompile().id}")
            "run next" -> {
                val result = queue.runNext()
                if (result == null) uiEngine.renderNotice("queue empty")
                else uiEngine.renderNotice("job ${result.item.id} exit=${result.exitCode}\n${result.outputTail}")
            }
            else -> uiEngine.renderNotice(renderer.render())
        }
        return RouterOutcome.CONTINUE
    }
}
