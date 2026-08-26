/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role

/**
 * Renders a [DiffContent] model into beautiful, themed terminal output.
 *
 * Design language:
 * - File headers in a branded rail block with file path and change summary
 * - Hunk headers in DIFF_HUNK role with the section label highlighted
 * - Added lines in DIFF_ADD with `+` prefix glyph
 * - Removed lines in DIFF_REMOVE with `-` prefix glyph
 * - Context lines in DIFF_CONTEXT
 * - Line numbers in a muted gutter when the terminal is wide enough
 * - A summary block at the bottom with total files, additions, and deletions
 *
 * Width-safe: nothing returned ever exceeds the requested width.
 */
class DiffContentRenderer(
    private val theme: TerminalTheme
) {
    /** Minimum width at which line-number gutters are shown. */
    private val GUTTER_THRESHOLD = 60

    /** Width of each line-number column. */
    private val LINE_NUMBER_WIDTH = 5

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()

    private fun railGlyph(): String = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL

    /**
     * Renders the full diff content as themed lines.
     *
     * @param diff The parsed diff content
     * @param width Terminal width
     * @param showLineNumbers Whether to show line-number gutters (auto-disabled on narrow terminals)
     * @param collapsedPaths Paths to show as collapsed (header only, hunks hidden)
     */
    fun render(
        diff: DiffContent,
        width: Int,
        showLineNumbers: Boolean = true,
        collapsedPaths: Set<String> = emptySet()
    ): List<String> {
        if (diff.files.isEmpty()) return listOf(theme.subdued("  (empty diff)"))

        val safeWidth = width.coerceAtLeast(20)
        val output = mutableListOf<String>()

        // Summary header
        output += summaryHeader(diff, safeWidth)
        output += ""

        diff.files.forEachIndexed { index, file ->
            output += fileHeader(file, safeWidth, index + 1, diff.totalFiles)
            if (file.displayPath !in collapsedPaths) {
                output += fileBody(file, safeWidth, showLineNumbers)
            } else {
                output += TerminalText.ellipsize(
                    "  " + theme.subdued("  ▸ ${file.hunks.size} hunks collapsed"),
                    safeWidth
                )
            }
            if (index < diff.files.lastIndex) output += ""
        }

        return output
    }

    /**
     * Renders a single file's diff.
     */
    fun renderFile(
        file: FileDiff,
        width: Int,
        showLineNumbers: Boolean = true
    ): List<String> {
        val safeWidth = width.coerceAtLeast(20)
        return listOf(fileHeader(file, safeWidth, 1, 1)) + fileBody(file, safeWidth, showLineNumbers)
    }

    /**
     * Compact one-line summary for inline display (e.g. in a toast or status bar).
     */
    fun compactSummary(diff: DiffContent): String {
        if (diff.files.isEmpty()) return "no changes"
        val parts = mutableListOf<String>()
        parts += "${diff.totalFiles} file${if (diff.totalFiles != 1) "s" else ""}"
        if (diff.totalAdditions > 0) parts += theme.paint(Role.DIFF_ADD, "+${diff.totalAdditions}")
        if (diff.totalDeletions > 0) parts += theme.paint(Role.DIFF_REMOVE, "-${diff.totalDeletions}")
        return parts.joinToString(" · ")
    }

    // ---- file header ---------------------------------------------------------

    private fun summaryHeader(diff: DiffContent, width: Int): String {
        val rail = theme.paint(Role.BRAND, railGlyph())
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val summary = buildString {
            append(theme.paint(Role.BRAND, "DIFF"))
            append(theme.subdued("  "))
            append(theme.strong("${diff.totalFiles} file${if (diff.totalFiles != 1) "s" else ""}"))
            append(theme.subdued("  "))
            if (diff.totalAdditions > 0) {
                append(theme.paint(Role.DIFF_ADD, "+${diff.totalAdditions}"))
                append(theme.subdued("  "))
            }
            if (diff.totalDeletions > 0) {
                append(theme.paint(Role.DIFF_REMOVE, "-${diff.totalDeletions}"))
            }
        }
        return TerminalText.ellipsize(rail + pad + summary, width)
    }

    private fun fileHeader(file: FileDiff, width: Int, index: Int, total: Int): String {
        val rail = theme.paint(Role.BRAND, railGlyph())
        val pad = " ".repeat(Glyphs.RAIL_PADDING)

        val status = when {
            file.isNewFile -> theme.paint(Role.DIFF_ADD, "new")
            file.isDeletedFile -> theme.paint(Role.DIFF_REMOVE, "deleted")
            file.isRename -> theme.paint(Role.STATUS_PENDING, "renamed")
            else -> theme.paint(Role.TEXT_SECONDARY, "modified")
        }

        val changeBadge = buildString {
            if (file.totalAdditions > 0) append(theme.paint(Role.DIFF_ADD, "+${file.totalAdditions}"))
            if (file.totalAdditions > 0 && file.totalDeletions > 0) append(theme.subdued("/"))
            if (file.totalDeletions > 0) append(theme.paint(Role.DIFF_REMOVE, "-${file.totalDeletions}"))
        }

        val counter = if (total > 1) theme.subdued(" [$index/$total]") else ""
        val path = theme.path(file.displayPath)

        return TerminalText.ellipsize(
            "$rail$pad$status $path $changeBadge$counter",
            width
        )
    }

    // ---- file body -----------------------------------------------------------

    private fun fileBody(file: FileDiff, width: Int, showLineNumbers: Boolean): List<String> {
        val output = mutableListOf<String>()
        val useGutter = showLineNumbers && width >= GUTTER_THRESHOLD

        file.hunks.forEachIndexed { hunkIndex, hunk ->
            output += hunkHeader(hunk, width)
            output += hunkLines(hunk, width, useGutter)
            if (hunkIndex < file.hunks.lastIndex) {
                output += theme.subdued("  " + "·".repeat((width - 4).coerceIn(1, 40)))
            }
        }

        return output
    }

    private fun hunkHeader(hunk: DiffHunk, width: Int): String {
        val rail = theme.paint(Role.DIFF_HUNK, railGlyph())
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val range = theme.paint(
            Role.DIFF_HUNK,
            "@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@"
        )
        val label = hunk.sectionLabel?.let { " " + theme.subdued(it) } ?: ""
        return TerminalText.ellipsize("$rail$pad$range$label", width)
    }

    private fun hunkLines(hunk: DiffHunk, width: Int, useGutter: Boolean): List<String> {
        val rail = railGlyph()
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val gutterWidth = if (useGutter) LINE_NUMBER_WIDTH * 2 + 1 else 0
        val innerWidth = (width - rail.length - Glyphs.RAIL_PADDING - gutterWidth - 2).coerceAtLeast(8)

        return hunk.lines
            .filter { it.kind != DiffLine.Kind.HUNK_HEADER }
            .map { line ->
                val (role, prefix) = when (line.kind) {
                    DiffLine.Kind.ADD -> Role.DIFF_ADD to "+"
                    DiffLine.Kind.REMOVE -> Role.DIFF_REMOVE to "-"
                    DiffLine.Kind.CONTEXT -> Role.DIFF_CONTEXT to " "
                    DiffLine.Kind.NO_NEWLINE -> Role.DIFF_CONTEXT to "\\"
                    else -> Role.DIFF_CONTEXT to " "
                }

                val gutter = if (useGutter) {
                    val old = line.oldLineNumber?.let { it.toString().padStart(LINE_NUMBER_WIDTH) }
                        ?: " ".repeat(LINE_NUMBER_WIDTH)
                    val new = line.newLineNumber?.let { it.toString().padStart(LINE_NUMBER_WIDTH) }
                        ?: " ".repeat(LINE_NUMBER_WIDTH)
                    theme.subdued("$old $new ")
                } else {
                    ""
                }

                val content = TerminalText.sanitize(line.text)
                val clipped = TerminalText.ellipsize(content, innerWidth)
                val paintedRail = theme.paint(role, rail)
                val paintedContent = theme.paint(role, "$prefix $clipped")

                TerminalText.ellipsize("$paintedRail$pad$gutter$paintedContent", width)
            }
    }
}
