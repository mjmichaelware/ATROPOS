/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * ATOM-UI-023: Box Semantic Role Mapping & Color Scheme Translation.
 *
 * Maps the 9 canonical modal and box roles (title, border, header, selected,
 * hover, muted, success, warning, error) across the 5 ATROPOS color schemes:
 * - atropos-dark
 * - electric-blue
 * - amber-crt
 * - solarized
 * - terminal
 */
enum class BoxRole {
    TITLE,
    BORDER,
    HEADER,
    SELECTED,
    HOVER,
    MUTED,
    SUCCESS,
    WARNING,
    ERROR;

    fun toRole(): Role = when (this) {
        TITLE -> Role.BRAND
        BORDER -> Role.BORDER_STRONG
        HEADER -> Role.SURFACE_HEADER
        SELECTED -> Role.ACCENT_SELECTION
        HOVER -> Role.ACCENT_FOCUS
        MUTED -> Role.TEXT_MUTED
        SUCCESS -> Role.STATUS_VERIFIED
        WARNING -> Role.STATUS_PENDING
        ERROR -> Role.STATUS_ERROR
    }
}

object BoxSemanticRoles {
    private const val RESET = "\u001B[0m"
    private const val BOLD = "\u001B[1m"
    private const val DIM = "\u001B[2m"

    // ANSI Palette per scheme
    private val SCHEME_PALETTES: Map<String, Map<BoxRole, String>> = mapOf(
        "atropos-dark" to mapOf(
            BoxRole.TITLE to "\u001B[38;5;196m",     // Red
            BoxRole.BORDER to "\u001B[38;5;238m",    // Subtle gray
            BoxRole.HEADER to "\u001B[38;5;250m",    // Crisp white-gray
            BoxRole.SELECTED to "\u001B[48;5;88m\u001B[38;5;255m", // Deep red bg + white
            BoxRole.HOVER to "\u001B[38;5;203m",     // Bright red
            BoxRole.MUTED to "\u001B[38;5;242m",     // Gray
            BoxRole.SUCCESS to "\u001B[38;5;40m",    // Green
            BoxRole.WARNING to "\u001B[38;5;214m",   // Amber
            BoxRole.ERROR to "\u001B[38;5;196m"      // Red
        ),
        "electric-blue" to mapOf(
            BoxRole.TITLE to "\u001B[38;5;39m",      // Bright cyan-blue
            BoxRole.BORDER to "\u001B[38;5;24m",     // Deep blue
            BoxRole.HEADER to "\u001B[38;5;75m",     // Light blue
            BoxRole.SELECTED to "\u001B[48;5;27m\u001B[38;5;255m", // Electric blue bg
            BoxRole.HOVER to "\u001B[38;5;81m",      // Neon cyan
            BoxRole.MUTED to "\u001B[38;5;67m",      // Slate blue
            BoxRole.SUCCESS to "\u001B[38;5;48m",    // Mint
            BoxRole.WARNING to "\u001B[38;5;220m",   // Yellow
            BoxRole.ERROR to "\u001B[38;5;198m"      // Magenta-red
        ),
        "amber-crt" to mapOf(
            BoxRole.TITLE to "\u001B[38;5;214m",     // Amber
            BoxRole.BORDER to "\u001B[38;5;130m",    // Dim amber
            BoxRole.HEADER to "\u001B[38;5;220m",    // Bright amber
            BoxRole.SELECTED to "\u001B[48;5;130m\u001B[38;5;232m", // Amber bg
            BoxRole.HOVER to "\u001B[38;5;226m",     // Brightest yellow-amber
            BoxRole.MUTED to "\u001B[38;5;94m",      // Dark amber
            BoxRole.SUCCESS to "\u001B[38;5;178m",   // Golden
            BoxRole.WARNING to "\u001B[38;5;208m",   // Orange-amber
            BoxRole.ERROR to "\u001B[38;5;166m"      // Red-amber
        ),
        "solarized" to mapOf(
            BoxRole.TITLE to "\u001B[38;5;136m",     // Yellow/Gold
            BoxRole.BORDER to "\u001B[38;5;240m",    // Base01
            BoxRole.HEADER to "\u001B[38;5;245m",    // Base0
            BoxRole.SELECTED to "\u001B[48;5;236m\u001B[38;5;253m", // Base02 bg
            BoxRole.HOVER to "\u001B[38;5;37m",      // Cyan
            BoxRole.MUTED to "\u001B[38;5;241m",     // Base01
            BoxRole.SUCCESS to "\u001B[38;5;64m",    // Green
            BoxRole.WARNING to "\u001B[38;5;166m",   // Orange
            BoxRole.ERROR to "\u001B[38;5;160m"      // Red
        ),
        "terminal" to mapOf(
            BoxRole.TITLE to BOLD,
            BoxRole.BORDER to DIM,
            BoxRole.HEADER to BOLD,
            BoxRole.SELECTED to "\u001B[7m",         // Inverse
            BoxRole.HOVER to BOLD,
            BoxRole.MUTED to DIM,
            BoxRole.SUCCESS to "\u001B[32m",
            BoxRole.WARNING to "\u001B[33m",
            BoxRole.ERROR to "\u001B[31m"
        )
    )

    fun colorCode(role: BoxRole, scheme: String): String {
        val cleanScheme = scheme.lowercase().trim()
        val palette = SCHEME_PALETTES[cleanScheme] ?: SCHEME_PALETTES["atropos-dark"]!!
        return palette[role] ?: ""
    }

    fun paint(role: BoxRole, scheme: String, text: String): String {
        val code = colorCode(role, scheme)
        return if (code.isEmpty()) text else "$code$text$RESET"
    }
}
