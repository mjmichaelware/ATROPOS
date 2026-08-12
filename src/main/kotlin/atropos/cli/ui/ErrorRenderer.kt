/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.cli.ui.design.Spacing
import atropos.core.security.RedactionFilter

/**
 * Error display renderer showing failure reason, recovery suggestions, and copyable details.
 * Implements Section E of ATROPOS: redundant channels (color + icon + text) for accessibility.
 *
 * Every operator-visible string here is redacted before paint. That matters more
 * in this renderer than anywhere else in the UI: the details block is labelled
 * "copy for support", so its whole purpose is to be pasted somewhere else. An
 * unredacted secret leaving through a block that invites forwarding is the exact
 * leak Phase 4 exists to prevent, so redaction happens at the boundary rather
 * than being left to each caller to remember.
 */
class ErrorRenderer(
    private val theme: TerminalTheme,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val responseExport: CopyDownloadResponse = CopyDownloadResponse(redactionFilter)
) {
    data class ErrorInfo(
        val title: String,
        val message: String,
        val suggestion: String? = null,
        val details: String? = null,
        val recovery: String? = null
    )

    /**
     * Render a user-facing error with recovery suggestions.
     * Ensures NO_COLOR/dumb terminal compatibility by including text labels.
     */
    fun render(rawError: ErrorInfo, width: Int): List<String> {
        val error = redacted(rawError)
        val safeWidth = width.coerceIn(1, 200)
        val output = mutableListOf<String>()

        // Error header with icon equivalent in text
        output += theme.paint(Role.STATUS_ERROR, "[ERROR]") + " " + theme.strong(error.title)

        // Main message
        output += ""
        output.addAll(AnsiLineWrapper.wrap(error.message, safeWidth).map { theme.paint(Role.TEXT_PRIMARY, it) })

        // Recovery suggestion (if present)
        if (!error.suggestion.isNullOrBlank()) {
            output += ""
            val suggestionLines = AnsiLineWrapper.wrap(error.suggestion, safeWidth - 13)
            output += theme.paint(Role.STATUS_VERIFIED, "[SUGGESTION]") + " " + (suggestionLines.firstOrNull() ?: "")
            output.addAll(suggestionLines.drop(1))
        }

        // Recovery action (if present)
        if (!error.recovery.isNullOrBlank()) {
            output += ""
            output += theme.paint(Role.ACCENT_FOCUS, "Try:")
            output += "  " + theme.code(error.recovery)
        }

        // Copyable details section (for debugging)
        if (!error.details.isNullOrBlank()) {
            val copyable = responseExport.copy(error.details)
            output += ""
            output += theme.surface.rule(safeWidth, Role.BORDER_SUBTLE)
            output += theme.paint(Role.TEXT_MUTED, "Details (copy/download ${copyable.bytes} bytes):")
            output += ""
            for (line in error.details.lines()) {
                output.addAll(AnsiLineWrapper.wrap(theme.code(line), safeWidth))
            }
            output += theme.surface.rule(safeWidth, Role.BORDER_SUBTLE)
        }

        return output
    }

    /**
     * Render a critical error that requires user attention.
     */
    fun renderCritical(error: ErrorInfo, width: Int): List<String> {
        val safeWidth = width.coerceIn(1, 200)
        val output = mutableListOf<String>()

        output += ""
        output += theme.surface.rule(safeWidth, Role.STATUS_ERROR)
        output += theme.paint(Role.STATUS_ERROR, "⚠ CRITICAL ERROR ⚠")
        output += theme.surface.rule(safeWidth, Role.STATUS_ERROR)
        output.addAll(render(error, safeWidth))
        output += theme.surface.rule(safeWidth, Role.STATUS_ERROR)
        output += ""

        return output
    }

    /**
     * Inline error badge for status lines (e.g., "provider error").
     */
    fun badge(message: String): String =
        theme.paint(Role.STATUS_ERROR, "●") + " " +
            theme.paint(Role.STATUS_ERROR, redactionFilter.redact(message))

    /**
     * Redacts every operator-visible field once, at the boundary.
     *
     * Done as a whole-record copy rather than per-field at each use site so a
     * field added to [ErrorInfo] later cannot quietly bypass redaction: the
     * compiler forces this function to name it.
     */
    private fun redacted(error: ErrorInfo): ErrorInfo = ErrorInfo(
        title = redactionFilter.redact(error.title),
        message = redactionFilter.redact(error.message),
        suggestion = error.suggestion?.let(redactionFilter::redact),
        details = error.details?.let(redactionFilter::redact),
        recovery = error.recovery?.let(redactionFilter::redact)
    )
}
