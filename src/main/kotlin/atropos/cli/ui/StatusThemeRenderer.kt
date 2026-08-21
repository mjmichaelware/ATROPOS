/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.ElectricThemes
import atropos.cli.ui.design.Role
import atropos.cli.ui.design.ThemeCatalog
import atropos.cli.ui.design.ThemePalette
import atropos.cli.ui.design.ThemePreference
import atropos.cli.ui.design.ColorTier

class StatusThemeRenderer(
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun renderStatus(activeId: String, currentPalette: ThemePalette, fromEnvironment: Boolean, hasChoiceStored: Boolean, width: Int): List<String> {
        val storageDesc = when {
            fromEnvironment -> "overridden by ATROPOS_THEME"
            hasChoiceStored -> "stored in ~/.atropos/theme"
            else -> "default (unstored)"
        }
        val body = listOf(
            surface.row("active theme", "${currentPalette.displayName} ($activeId)", width),
            surface.row("colour depth", theme.tier.name.lowercase(), width),
            surface.row("selection state", storageDesc, width),
            surface.hint("commands: /theme list · /theme preview · /theme <id> · /theme reset", width)
        )
        return surface.block("THEME ENGINE", body, width, Role.BRAND)
    }

    fun renderList(activeId: String, width: Int): List<String> {
        val body = ThemeCatalog.all.map { palette ->
            val isActive = palette.id.equals(activeId, ignoreCase = true)
            val indicator = if (isActive) "active" else "available"
            val health = if (isActive) atropos.cli.ui.design.Health.VERIFIED else atropos.cli.ui.design.Health.PENDING
            surface.statusRow(palette.id, palette.displayName, health, width)
        }
        return surface.block("AVAILABLE THEMES", body, width, Role.BRAND)
    }

    fun renderPreview(width: Int): List<String> {
        val tier = theme.tier
        val body = ThemeCatalog.all.map { palette ->
            val paint = { role: Role, text: String ->
                val sgr = palette.style(role, tier)
                if (sgr.isEmpty() || tier == ColorTier.NONE) text else "\u001B[${sgr}m$text\u001B[0m"
            }
            val brand = paint(Role.BRAND, "ATROPOS")
            val running = paint(Role.STATUS_RUNNING, "running")
            val verified = paint(Role.STATUS_VERIFIED, "verified")
            val error = paint(Role.STATUS_ERROR, "error")
            val selection = paint(Role.ACCENT_SELECTION, "selected")
            val previewText = "$brand  $running  $verified  $error  $selection"
            surface.row(palette.id, previewText, width)
        } + listOf(
            surface.hint("verified, pending, and error keep consistent status colors in every theme", width)
        )
        return surface.block("THEME PREVIEW (${tier.name.lowercase()})", body, width, Role.BRAND)
    }
}
