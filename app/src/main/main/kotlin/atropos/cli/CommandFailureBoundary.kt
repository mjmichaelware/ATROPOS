/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.security.RedactionFilter

/**
 * Keeps one failing command from ending the session.
 *
 * Command handlers used to throw straight through the router into `main`'s
 * startup `catch`, which runs `ui.cleanup()` in its `finally` and lets the
 * process exit. To an operator that looks like the tool quitting: the prompt
 * disappears and the shell comes back, with a message that says "startup
 * failed" for a command typed long after startup.
 *
 * The failure that exposed this was a checked `NoSuchFileException`. Handlers
 * that guard themselves tend to catch `RuntimeException`, so anything under
 * `java.io`/`java.nio` walks past them untouched. This boundary catches
 * `Exception` rather than `RuntimeException` for exactly that reason.
 *
 * `Error` is deliberately not caught. `OutOfMemoryError` and
 * `StackOverflowError` mean the runtime itself is no longer trustworthy, and
 * continuing the loop would hide that.
 */
class CommandFailureBoundary(
    private val uiEngine: AnsiTerminalEngine,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /**
     * Runs [action], returning its outcome. Any [Exception] is rendered as a
     * command error and reported as [RouterOutcome.CONTINUE] so the prompt
     * survives. [label] names the command for the operator.
     */
    fun guard(label: String, action: () -> RouterOutcome): RouterOutcome =
        try {
            action()
        } catch (failure: Exception) {
            uiEngine.renderError(describe(label, failure))
            RouterOutcome.CONTINUE
        }

    /**
     * Failure text is redacted before display. A message is frequently a
     * filesystem path or a provider payload, and this is a UI surface, so it
     * falls under the same no-raw-secret rule as any other rendered output.
     */
    private fun describe(label: String, failure: Exception): String {
        val kind = failure.javaClass.simpleName
        val detail = redactionFilter.compact(failure.message.orEmpty()).trim()
        return if (detail.isEmpty()) {
            "$label failed ($kind)"
        } else {
            "$label failed ($kind): $detail"
        }
    }
}
