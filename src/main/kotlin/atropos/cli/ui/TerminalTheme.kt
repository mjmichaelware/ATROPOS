/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ColorTier
import atropos.cli.ui.design.Role
import atropos.cli.ui.design.Surface
import atropos.cli.ui.design.ThemeCatalog
import atropos.cli.ui.design.ThemePalette
import atropos.cli.ui.design.ThemePreference
import atropos.core.phase20.AnsiScheme
import atropos.core.observability.AccessibilitySettings

/**
 * Resolves semantic [Role]s to SGR sequences for the active theme and terminal
 * capability tier.
 *
 * The named helpers below are the historical renderer API and are kept so every
 * existing call site keeps working; they are now thin aliases over roles, so
 * they pick up theme and capability changes for free. New renderers should
 * prefer [surface] and [paint] directly.
 */
class TerminalTheme(
    private val capabilities: ConfigurationManager,
    /**
     * Re-resolved per instance from [ThemePreference], so a theme chosen with
     * `/theme` applies to renderers built after it without a restart, and
     * survives one.
     */
    private val palette: ThemePalette = ThemeCatalog.byId(ThemePreference.resolve()),
    private val tierOverride: ColorTier? = null
) {
    val accessibility: AccessibilitySettings = AccessibilitySettings.fromEnvironment()

    val colorEnabled: Boolean
        get() = capabilities.isColorEnabled

    /** Terminal capability tier, re-read each call so a theme switch takes effect live. */
    val tier: ColorTier
        get() = tierOverride ?: ColorTier.detect(
            colorEnabled = capabilities.isColorEnabled,
            term = System.getenv("TERM"),
            colorterm = System.getenv("COLORTERM")
        )

    val themeId: String get() = palette.id
    val themeName: String get() = palette.displayName

    /** Composition primitives bound to this theme. */
    val surface: Surface = Surface { role, text -> paint(role, text) }
        .also { it.asciiOnly = System.getenv("ATROPOS_ASCII").isNullOrBlank().not() }

    /** Paints text with a semantic role. The single styling entry point. */
    fun paint(role: Role, text: String): String {
        if (text.isEmpty()) return text
        AnsiScheme.assertNoRawEscapes(text)
        val accessibleRole = if (accessibility.isHighContrast && role == Role.TEXT_MUTED) {
            Role.TEXT_PRIMARY
        } else {
            role
        }
        val sgr = palette.style(accessibleRole, tier)
        return if (sgr.isEmpty()) text else "\u001B[${sgr}m$text\u001B[0m"
    }

    fun accessibleLabel(key: String, fallback: String): String =
        accessibility.label(key, fallback)

    fun focus(text: String): String = accessibility.focus(text)

    // ---- established renderer API (aliases over roles) ----------------------

    fun brand(text: String): String = paint(Role.BRAND, text)
    fun success(text: String): String = paint(Role.STATUS_VERIFIED, text)
    fun error(text: String): String = paint(Role.STATUS_ERROR, text)
    fun warning(text: String): String = paint(Role.STATUS_PENDING, text)
    fun metadata(text: String): String = paint(Role.TEXT_SECONDARY, text)
    fun subdued(text: String): String = paint(Role.TEXT_MUTED, text)
    fun strong(text: String): String = paint(Role.TEXT_PRIMARY, text)
    fun path(text: String): String = paint(Role.PATH, text)
    fun code(text: String): String = paint(Role.CODE, text)
    fun headerBrand(text: String): String = paint(Role.BRAND, text)
    fun headerText(text: String): String = paint(Role.SURFACE_HEADER, text)
    fun footer(text: String): String = paint(Role.SURFACE_FOOTER, text)
    fun selection(text: String): String = paint(Role.ACCENT_SELECTION, text)

    fun reset(): String = if (colorEnabled) "\u001B[0m" else ""
}
