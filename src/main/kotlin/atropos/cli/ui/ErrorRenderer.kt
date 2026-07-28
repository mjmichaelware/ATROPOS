/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.cli.ui.design.Spacing

/**
 * Error display renderer showing failure reason, recovery suggestions, and copyable details.
 * Implements Section E of ATROPOS: redundant channels (color + icon + text) for accessibility.
 */
class ErrorRenderer(
    private val theme: TerminalTheme
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
    fun render(error: ErrorInfo, width: Int): List<String> {
        val safeWidth = width.coerceIn(40, 200)
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
            output += ""
            output += theme.surface.rule(safeWidth, Role.BORDER_SUBTLE)
            output += theme.paint(Role.TEXT_MUTED, "Details (copy for support):")
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
        val safeWidth = width.coerceIn(40, 200)
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
        theme.paint(Role.STATUS_ERROR, "●") + " " + theme.paint(Role.STATUS_ERROR, message)
}
