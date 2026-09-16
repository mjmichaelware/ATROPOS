/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role
import atropos.core.security.RedactionFilter

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
    private val theme: TerminalTheme,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val thinkingFilter: ThinkingFilter = ThinkingFilter()
) {
    private val railGlyph: String
        get() = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL

    private val pad = " ".repeat(Glyphs.RAIL_PADDING)

    /** Returns the current thinking filter level. */
    fun currentThinkingLevel(): ThinkingLevel = thinkingFilter.current

    /** Cycles the thinking filter to the next level (L1→L2→L3→L1). */
    fun cycleThinkingLevel(): ThinkingLevel = thinkingFilter.cycle()

    /** Sets the thinking filter to a specific level. */
    fun setThinkingLevel(level: ThinkingLevel) {
        thinkingFilter.setLevel(level)
    }

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
            theme.paint(Role.STATUS_FAILED, TerminalText.sanitize(redactionFilter.redact(message)))

    fun activity(frame: String): String =
        theme.paint(Role.STATUS_RUNNING, railGlyph) + pad + theme.warning(frame)

    /**
     * Renders a disclosure row summary (collapsed state).
     */
    fun disclosureSummary(row: DisclosureRow): String {
        val icon = if (row.isExpanded) "▾" else "▸"
        val rail = theme.paint(Role.STATUS_RUNNING, icon)
        val label = theme.paint(Role.BRAND, row.kind.label)
        val summaryText = TerminalText.sanitize(row.summary)
        return "$rail $label $summaryText"
    }

    /**
     * Renders disclosure detail lines (expanded state).
     */
    fun disclosureDetail(row: DisclosureRow): List<String> {
        if (!row.isExpanded) return emptyList()
        return AnsiLineWrapper.wrap(row.detail, 80).map { "  $it" }
    }

    /**
     * Renders the full transcript for display in the viewport.
     */
    fun renderTranscript(
        buffer: TranscriptBuffer,
        width: Int,
        height: Int
    ): List<String> {
        if (buffer.isEmpty) return emptyList()
        val lines = mutableListOf<String>()
        var currentDepth = 1
        for (entry in buffer.entries()) {
            when (entry) {
                is TranscriptEntry.Text -> {
                    val wrapped = AnsiLineWrapper.wrap(entry.value, width)
                    lines.addAll(wrapped)
                    // Track depth markers for thinking content
                    currentDepth = 1
                }
                is TranscriptEntry.Disclosure -> {
                    val row = entry.row
                    // Filter thinking disclosures based on current thinking level
                    if (row.kind == DisclosureKind.THINKING) {
                        val allowedDepth = thinkingFilter.current.depth
                        // For thinking content, we use a simple heuristic:
                        // If not expanded, show summary only (L1)
                        // If expanded, show detail up to current level
                        if (!row.isExpanded) {
                            // L1: Always show summary
                            lines.add(disclosureSummary(row))
                        } else if (allowedDepth >= 2) {
                            // L2/L3: Show summary + detail
                            lines.add(disclosureSummary(row))
                            lines.addAll(disclosureDetail(row))
                        } else {
                            // L1 but expanded - just show summary
                            lines.add(disclosureSummary(row))
                        }
                    } else {
                        // Non-thinking disclosures always render normally
                        lines.add(disclosureSummary(row))
                        lines.addAll(disclosureDetail(row))
                    }
                }
            }
            val scrollOffset = buffer.currentScrollOffset
            val maximumOffset = (lines.size - height).coerceAtLeast(0)
            val start = scrollOffset.coerceIn(0, maximumOffset)
            val end = (start + height).coerceAtMost(lines.size)
            return lines.subList(start, end)
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
            theme.paint(Role.STATUS_FAILED, TerminalText.sanitize(redactionFilter.redact(message)))

    fun activity(frame: String): String =
        theme.paint(Role.STATUS_RUNNING, railGlyph) + pad + theme.warning(frame)

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()
}
