/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.LiveThinkingRenderer
import atropos.core.thinking.ThinkingDepth

/**
 * `/thinking [1|2|3]` — how much of the engine's reasoning to show.
 *
 * `HOE-B03` defines the three levels and `HOE-E04` requires each surface to own
 * its own verbosity. The levels existed and were reachable only over the
 * bridge, so a CLI operator could not see reasoning at any depth — the control
 * existed for the phone and not for the machine actually running the work.
 *
 * Changing depth replays what has already been emitted. An operator typing
 * `/thinking 3` twelve minutes into a run is asking what *has been* happening;
 * without the replay they would wait for the next line to see any effect, and
 * on a slow step that is minutes of nothing after asking for more.
 */
class ThinkingCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: LiveThinkingRenderer = LiveThinkingRenderer(uiEngine)
) {
    fun execute(tokens: List<String>): RouterOutcome {
        val argument = tokens.getOrNull(1)?.lowercase()

        when {
            argument == null || argument == "status" -> renderStatus()

            argument == "off" || argument == "1" || argument == "outline" -> set(ThinkingDepth.L1)
            argument == "2" || argument == "reasoning" -> set(ThinkingDepth.L2)
            argument == "3" || argument == "full" || argument == "all" -> set(ThinkingDepth.L3)

            argument == "replay" -> {
                renderer.replay()
                uiEngine.renderNotice("(replayed at ${label(renderer.depth())})")
            }

            else -> uiEngine.renderError(
                "usage: /thinking [1|2|3|replay|status]\n" +
                    ThinkingDepth.entries.joinToString("\n") {
                        "  ${it.level}  ${it.label}"
                    }
            )
        }
        return RouterOutcome.CONTINUE
    }

    private fun set(depth: ThinkingDepth) {
        renderer.expand(depth)
        uiEngine.renderNotice(
            "Thinking depth is now ${label(depth)}. Showing what has happened so far:"
        )
        renderer.replay()
    }

    private fun renderStatus() {
        uiEngine.renderNotice(
            buildString {
                appendLine("Thinking depth: ${label(renderer.depth())}")
                appendLine()
                ThinkingDepth.entries.forEach { depth ->
                    val marker = if (depth == renderer.depth()) ">" else " "
                    appendLine("  $marker ${depth.level}  ${depth.label}")
                }
                appendLine()
                append("  /thinking 3 to follow a long run as it works.")
            }
        )
    }

    private fun label(depth: ThinkingDepth): String = "L${depth.level} ${depth.label}"
}
