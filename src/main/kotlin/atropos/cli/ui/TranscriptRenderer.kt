/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/**
 * Transcript entries in the pinned reference's layout language.
 *
 * The reference marks every block with a coloured left rail plus two columns of
 * padding, tinting the rail per block kind so user, assistant and tool output
 * are distinguishable without any box chrome. It draws no corners, no closing
 * rules, and no `•`/`✓`/`✗` glyph prefixes on transcript lines — the rail
 * carries that signal.
 */
class TranscriptRenderer(
    private val theme: TerminalTheme
) {
    private val railGlyph: String
        get() = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL

    private val pad = " ".repeat(Glyphs.RAIL_PADDING)

    /**
     * Prefixes a line with a rail tinted for its block kind.
     *
     * Content already carrying a rail — e.g. output shaped by
     * [RailBlockFormatter] — is passed through untouched so blocks are never
     * double-railed, and is deliberately not sanitized, since stripping ANSI
     * would discard the colour that shaping applied.
     */
    private fun rail(role: Role, text: String): String {
        // Shaped content leads with an SGR sequence, so the rail is only
        // visible after stripping ANSI. Checking the raw string would miss it
        // and double-rail the block.
        val lead = TerminalText.stripAnsi(text).trimStart()
        return if (lead.startsWith(railGlyph) || lead.startsWith(Glyphs.Ascii.RAIL)) {
            text
        } else {
            theme.paint(role, railGlyph) + pad + TerminalText.sanitize(text)
        }
    }

    fun user(mode: String, prompt: String): String =
        theme.paint(Role.ACCENT_FOCUS, railGlyph) + pad +
            theme.metadata("${mode.lowercase()} ") +
            TerminalText.sanitize(prompt)

    fun assistantHeader(provider: String): String =
        theme.paint(Role.BRAND, railGlyph) + pad + theme.brand(provider.lowercase())

    fun assistantBody(renderedMarkdown: String): List<String> =
        renderedMarkdown.lines().map { line ->
            theme.paint(Role.BRAND, railGlyph) + pad + TerminalText.sanitize(line)
        }

    /** Reference blocks close with spacing, not a drawn footer rule. */
    fun assistantFooter(): String = ""

    fun notice(message: String): String = rail(Role.TEXT_MUTED, message)

    fun success(message: String): String = rail(Role.STATUS_COMPLETE, message)

    fun error(message: String): String =
        theme.paint(Role.STATUS_FAILED, railGlyph) + pad +
            theme.paint(Role.STATUS_FAILED, TerminalText.sanitize(message))

    fun activity(frame: String): String =
        theme.paint(Role.STATUS_RUNNING, railGlyph) + pad + theme.warning(frame)

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()
}
