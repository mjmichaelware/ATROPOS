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

    private val theme = atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())
    private val surface get() = theme.surface

    fun execute(tokens: List<String>): RouterOutcome {
        val width = uiEngine.viewportWidth
        when (tokens.firstOrNull()?.lowercase()) {
            "/pause" -> request(InterruptLevel.FREEZE)
            "/resume" -> resume()
            else -> when (val sub = tokens.getOrNull(1)?.lowercase()) {
                null, "status" -> renderStatus()
                "clear" -> {
                    InterruptRegistry.controller.clear()
                    uiEngine.renderBlock(
                        surface.block(
                            "INTERRUPT CLEAR",
                            listOf(surface.statusRow("status", "Interrupt cleared. The run may continue.", atropos.cli.ui.design.Health.VERIFIED, width)),
                            width,
                            atropos.cli.ui.design.Role.BRAND
                        )
                    )
                }
                else -> request(
                    when (sub) {
                        "freeze" -> InterruptLevel.FREEZE
                        "soft" -> InterruptLevel.SOFT
                        else -> InterruptLevel.HARD
                    }
                )
            }
        }
        return RouterOutcome.CONTINUE
    }

    private fun renderStatus() {
        val width = uiEngine.viewportWidth
        val state = InterruptRegistry.status()
        val body = buildList {
            val req = state.requested
            add(surface.statusRow("requested level", req?.level?.canonical ?: "none", if (req != null) atropos.cli.ui.design.Health.PENDING else atropos.cli.ui.design.Health.VERIFIED, width))
            add(surface.row("source", req?.source ?: "none", width))
            add(surface.row("active", if (state.active) "yes" else "no", width))
            if (state.frozen != null) {
                add(surface.statusRow("frozen run", state.frozen.runId, atropos.cli.ui.design.Health.PENDING, width))
                add(surface.row("frozen point", state.frozen.resumePoint, width))
            }
        }
        uiEngine.renderBlock(surface.block("INTERRUPT STATUS", body, width, atropos.cli.ui.design.Role.BRAND))
    }

    private fun request(level: InterruptLevel) {
        val width = uiEngine.viewportWidth
        val state = InterruptRegistry.request(level, "operator")
        val pending = state.requested
        if (pending != null && pending.level != level) {
            val body = listOf(
                surface.statusRow("status", "A ${pending.level.canonical} interrupt stands.", atropos.cli.ui.design.Health.ERROR, width),
                surface.hint("It is not weakened by ${level.canonical}.", width)
            )
            uiEngine.renderBlock(surface.block("INTERRUPT REFUSED", body, width, atropos.cli.ui.design.Role.BRAND))
            return
        }
        val body = listOf(
            surface.statusRow("status", "${level.canonical} interrupt requested", atropos.cli.ui.design.Health.PENDING, width),
            surface.hint(level.description, width),
            surface.hint("The run stops at its next safe boundary.", width)
        )
        uiEngine.renderBlock(surface.block("INTERRUPT PENDING", body, width, atropos.cli.ui.design.Role.BRAND))
    }

    private fun resume() {
        val width = uiEngine.viewportWidth
        val frozen = InterruptRegistry.resume()
        if (frozen == null) {
            uiEngine.renderBlock(
                surface.block(
                    "RESUME REFUSED",
                    listOf(surface.statusRow("status", "Nothing is frozen.", atropos.cli.ui.design.Health.ERROR, width)),
                    width,
                    atropos.cli.ui.design.Role.BRAND
                )
            )
            return
        }
        val body = buildList {
            add(surface.statusRow("status", "Resuming ${frozen.runId}", atropos.cli.ui.design.Health.VERIFIED, width))
            add(surface.row("resume point", frozen.resumePoint, width))
            if (frozen.evidencePaths.isEmpty()) {
                add(surface.hint("No evidence had been produced before the freeze.", width))
            } else {
                add(surface.sectionHeading("EVIDENCE PRODUCED", width))
                frozen.evidencePaths.forEach { add("  $it") }
            }
        }
        uiEngine.renderBlock(surface.block("RESUMING RUN", body, width, atropos.cli.ui.design.Role.BRAND))
    }

    /** Whether a running loop should stop now. Called at loop boundaries. */
    fun shouldStop(): Boolean = InterruptRegistry.controller.shouldStop()

    /** Takes the pending interrupt on behalf of a loop that has stopped. */
    fun take(runId: String, resumePoint: String?): InterruptOutcome =
        InterruptRegistry.take(runId, resumePoint)
}
