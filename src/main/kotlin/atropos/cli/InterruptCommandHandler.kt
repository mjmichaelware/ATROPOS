/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.interrupt.InterruptLevel
import atropos.core.interrupt.InterruptOutcome
import atropos.core.interrupt.InterruptRegistry

/**
 * `/interrupt`, `/pause`, `/resume` — stopping without losing the work.
 *
 * `SUP.UX.INTERRUPT-PRIMITIVE`: "Wire SIGINT / Ctrl-C and explicit `interrupt` /
 * `pause` / `resume` commands." Competitors treat interrupt as kill, which on a
 * long phone job teaches the operator never to interrupt — so the job runs to
 * an outcome nobody wanted because stopping it was more expensive than waiting.
 *
 * `/pause` is `freeze`, not `soft`. An operator typing pause means "I want this
 * back later"; soft is a stop that happens to be resumable, and defaulting to
 * it would occasionally leave nothing to come back to.
 */
class InterruptCommandHandler(private val uiEngine: AnsiTerminalEngine) {

    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.firstOrNull()?.lowercase()) {
            "/pause" -> request(InterruptLevel.FREEZE)
            "/resume" -> resume()
            else -> when (val sub = tokens.getOrNull(1)?.lowercase()) {
                null, "status" -> uiEngine.renderNotice(InterruptRegistry.render())
                "clear" -> {
                    InterruptRegistry.controller.clear()
                    uiEngine.renderNotice("Interrupt cleared. The run may continue.")
                }
                else -> {
                    val level = InterruptLevel.fromCanonical(sub)
                    if (level == null) {
                        uiEngine.renderError(
                            "usage: /interrupt [soft|hard|freeze|status|clear]\n" +
                                InterruptLevel.entries.joinToString("\n") {
                                    "  ${it.canonical.padEnd(7)} ${it.description}"
                                }
                        )
                    } else {
                        request(level)
                    }
                }
            }
        }
        return RouterOutcome.CONTINUE
    }

    private fun request(level: InterruptLevel) {
        val state = InterruptRegistry.request(level, "operator")
        val pending = state.requested
        if (pending != null && pending.level != level) {
            // A stronger interrupt already stands. Saying "requested" here
            // would report a gentler stop than the one that will happen.
            uiEngine.renderNotice(
                "A ${pending.level.canonical} interrupt already stands and is not weakened by " +
                    "${level.canonical}. ${state.render()}"
            )
            return
        }
        uiEngine.renderNotice(
            "${level.canonical} interrupt requested — ${level.description}.\n" +
                "The run stops at its next safe boundary; /interrupt status shows when it has."
        )
    }

    private fun resume() {
        val frozen = InterruptRegistry.resume()
        if (frozen == null) {
            uiEngine.renderNotice("Nothing is frozen. /pause freezes the current run.")
            return
        }
        uiEngine.renderNotice(
            "Resuming ${frozen.runId} from '${frozen.resumePoint}'.\n" +
                if (frozen.evidencePaths.isEmpty()) {
                    "No evidence had been produced before the freeze."
                } else {
                    "Evidence already produced:\n" +
                        frozen.evidencePaths.joinToString("\n") { "  $it" }
                }
        )
    }

    /** Whether a running loop should stop now. Called at loop boundaries. */
    fun shouldStop(): Boolean = InterruptRegistry.controller.shouldStop()

    /** Takes the pending interrupt on behalf of a loop that has stopped. */
    fun take(runId: String, resumePoint: String?): InterruptOutcome =
        InterruptRegistry.take(runId, resumePoint)
}
