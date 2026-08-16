/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.errors.SystemExceptionHandler
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ErrorRenderer
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
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val systemHandler: SystemExceptionHandler = SystemExceptionHandler(redactionFilter)
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
            // Also to stderr, through the last-resort sink. The transcript is
            // the operator's record and can be scrolled away or cleared; a
            // command that failed should still be findable in a piped log,
            // and SystemExceptionHandler is what bounds and redacts it there.
            systemHandler.handle(failure)
            uiEngine.renderErrorDetail(
                ErrorRenderer.ErrorInfo(
                    title = "$label failed",
                    message = describe(label, failure),
                    suggestion = suggestionFor(failure),
                    // The exception type and message, not a stack trace. A
                    // trace names ATROPOS internals the operator did not write
                    // and cannot act on, and it is long enough to push the
                    // failure itself off the screen.
                    details = "${failure.javaClass.name}: ${redactionFilter.compact(failure.message.orEmpty())}"
                )
            )
            RouterOutcome.CONTINUE
        }

    /**
     * What to try next, for the failure kinds that have a definite answer.
     *
     * Null for everything else. A generic "check your input" is worse than no
     * suggestion: it occupies the line where a real recovery would go and
     * teaches the operator to stop reading the field.
     */
    private fun suggestionFor(failure: Exception): String? = when (failure) {
        is java.nio.file.NoSuchFileException, is java.io.FileNotFoundException ->
            "the path does not exist — check it with /ls, or /cd to the directory that holds it"
        is java.nio.file.AccessDeniedException ->
            "permission denied on that path; on Termux, storage outside \$HOME needs termux-setup-storage"
        is java.net.ConnectException, is java.net.UnknownHostException ->
            "the provider was unreachable — /providers shows which one is active and whether it is configured"
        else -> null
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
