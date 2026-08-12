/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.design.ColorTier
import atropos.cli.ui.design.Role
import atropos.cli.ui.design.ThemeCatalog
import atropos.cli.ui.design.ThemePreference

/**
 * `/theme` — see the palette, and change it.
 *
 * Source Doc 5 asks for a red default plus deep electric blue, orange, yellow
 * and purple, consistent across every feature. The palettes were added and
 * nothing could select them except `ATROPOS_THEME` in the environment — which
 * a phone operator cannot set before tapping a launcher, so on the target
 * device the choice did not exist.
 *
 * `preview` renders each theme's own tokens in that theme, because a list of
 * names tells you nothing about what you are choosing. What it shows is the
 * roles that carry meaning — brand, running, verified, error, selection — not
 * a colour swatch: the question an operator is answering is "will I be able to
 * read a failure in this", and only the semantic roles answer it.
 */
class ThemeCommandHandler(private val uiEngine: AnsiTerminalEngine) {

    fun execute(tokens: List<String>): RouterOutcome {
        when (val argument = tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> renderStatus()
            "list" -> renderList()
            "preview" -> renderPreview()
            "reset" -> reset()
            else -> set(argument)
        }
        return RouterOutcome.CONTINUE
    }

    private fun renderStatus() {
        val active = ThemePreference.resolve()
        val theme = ThemeCatalog.byId(active)
        val fromEnvironment = System.getenv("ATROPOS_THEME")?.isNotBlank() == true

        uiEngine.renderNotice(
            buildString {
                appendLine("Theme: ${theme.displayName} (${theme.id})")
                appendLine("Colour depth: ${TerminalTheme(ConfigurationManager()).tier.name.lowercase()}")
                if (fromEnvironment) {
                    appendLine("Set by ATROPOS_THEME, which overrides the stored choice.")
                } else if (ThemePreference.read() != null) {
                    appendLine("Stored in ~/.atropos/theme.")
                } else {
                    appendLine("No choice stored; this is the default.")
                }
                appendLine()
                append("  /theme list · /theme preview · /theme <id> · /theme reset")
            }
        )
    }

    private fun renderList() {
        val active = ThemePreference.resolve()
        uiEngine.renderNotice(
            buildString {
                appendLine("Themes")
                ThemeCatalog.all.forEach { theme ->
                    val marker = if (theme.id.equals(active, ignoreCase = true)) ">" else " "
                    appendLine("  $marker ${theme.id.padEnd(16)} ${theme.displayName}")
                }
            }.trimEnd()
        )
    }

    /**
     * Each theme rendered in its own palette.
     *
     * Built by constructing a [TerminalTheme] per palette rather than by
     * painting with the active one, so what you see is genuinely that theme at
     * this terminal's colour depth — a preview drawn in the current theme would
     * show every option looking identical.
     */
    private fun renderPreview() {
        val capabilities = ConfigurationManager()
        val tier = TerminalTheme(capabilities).tier

        uiEngine.renderNotice(
            buildString {
                appendLine("Theme preview at ${tier.name.lowercase()} colour depth")
                appendLine()
                ThemeCatalog.all.forEach { palette ->
                    val paint = { role: Role, text: String ->
                        val sgr = palette.style(role, tier)
                        if (sgr.isEmpty() || tier == ColorTier.NONE) text else "[${sgr}m$text[0m"
                    }
                    appendLine(
                        "  ${palette.id.padEnd(16)} " +
                            paint(Role.BRAND, "ATROPOS") + "  " +
                            paint(Role.STATUS_RUNNING, "running") + "  " +
                            paint(Role.STATUS_VERIFIED, "verified") + "  " +
                            paint(Role.STATUS_ERROR, "error") + "  " +
                            paint(Role.ACCENT_SELECTION, " selected ")
                    )
                }
                appendLine()
                append("  Verified, pending and error keep their colours in every theme, ")
                append("so a failure reads the same whichever accent you pick.")
            }
        )
    }

    private fun set(requested: String) {
        val match = ThemeCatalog.all.firstOrNull { it.id.equals(requested, ignoreCase = true) }
            ?: ThemeCatalog.all.firstOrNull { it.id.endsWith("-$requested", ignoreCase = true) }

        if (match == null) {
            uiEngine.renderError(
                "No theme called '$requested'. Available: " +
                    ThemeCatalog.all.joinToString(", ") { it.id }
            )
            return
        }

        if (!ThemePreference.write(match.id)) {
            uiEngine.renderError(
                "Could not store the theme choice, so it would revert on restart. " +
                    "Check that ~/.atropos is writable."
            )
            return
        }

        val overridden = System.getenv("ATROPOS_THEME")?.isNotBlank() == true
        uiEngine.renderNotice(
            buildString {
                appendLine("Theme set to ${match.displayName}.")
                if (overridden) {
                    appendLine(
                        "ATROPOS_THEME is set in this environment and overrides it for this session; " +
                            "unset it to see the stored choice."
                    )
                }
                append("Restart ATROPOS to repaint everything in it.")
            }
        )
    }

    private fun reset() {
        ThemePreference.clear()
        uiEngine.renderNotice("Theme reset to ${ThemeCatalog.DEFAULT_ID}. Restart to repaint.")
    }
}
