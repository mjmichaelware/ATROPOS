/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.BoxRole
import atropos.cli.ui.design.BoxSemanticRoles

/**
 * ATOM-UI-024: Device-Agnostic Beautiful Box System.
 *
 * Implements:
 * - Rounded (╭─╮│╰╯), heavy (┏━┓┃┗┛), and double (╔═╗║╚╝) borders.
 * - Centered title chrome with ATROPOS branding.
 * - Always-visible navigation chrome template.
 * - Semantic color roles from DesignTokens.
 * - Responsive breakpoints for CLI, Web terminal resize, and Android APK orientation.
 */
class BeautifulBoxSystem(
    private val scheme: String = "atropos-dark"
) {
    enum class BorderStyle(
        val topLeft: Char,
        val topRight: Char,
        val bottomLeft: Char,
        val bottomRight: Char,
        val horizontal: Char,
        val vertical: Char,
        val tLeft: Char,
        val tRight: Char,
        val cross: Char
    ) {
        ROUNDED('╭', '╮', '╰', '╯', '─', '│', '├', '┤', '┼'),
        HEAVY('┏', '┓', '┗', '┛', '━', '┃', '┣', '┫', '╋'),
        DOUBLE('╔', '╗', '╚', '╝', '═', '║', '╠', '╣', '╬')
    }

    enum class Breakpoint(val minWidth: Int) {
        ULTRA_NARROW(0),
        NARROW(40),
        STANDARD(80),
        WIDE(120);

        companion object {
            fun fromWidth(width: Int): Breakpoint = when {
                width >= 120 -> WIDE
                width >= 80 -> STANDARD
                width >= 40 -> NARROW
                else -> ULTRA_NARROW
            }
        }
    }

    companion object {
        const val NAVIGATION_CHROME_DEFAULT =
            "Keyboard: ↑/↓ Navigate  ←/→ Expand/Switch  Tab Cycle  Enter Select  Esc Close"
    }

    fun renderBox(
        title: String = "ATROPOS CLI",
        contentLines: List<String>,
        width: Int,
        borderStyle: BorderStyle = BorderStyle.ROUNDED,
        navigationChrome: String = NAVIGATION_CHROME_DEFAULT
    ): List<String> {
        val safeWidth = width.coerceAtLeast(36)
        val innerWidth = safeWidth - 4
        val breakpoint = Breakpoint.fromWidth(safeWidth)
        val lines = mutableListOf<String>()

        val bColor = { text: String -> BoxSemanticRoles.paint(BoxRole.BORDER, scheme, text) }
        val tColor = { text: String -> BoxSemanticRoles.paint(BoxRole.TITLE, scheme, text) }
        val nColor = { text: String -> BoxSemanticRoles.paint(BoxRole.MUTED, scheme, text) }

        // 1. Centered Title Top Border
        val cleanTitle = " $title "
        val rem = (safeWidth - cleanTitle.length - 2).coerceAtLeast(2)
        val left = rem / 2
        val right = rem - left
        val topBorder = bColor("${borderStyle.topLeft}${borderStyle.horizontal.toString().repeat(left)}") +
            tColor(cleanTitle) +
            bColor("${borderStyle.horizontal.toString().repeat(right)}${borderStyle.topRight}")
        lines.add(topBorder)

        // 2. Navigation Chrome (if not ultra-narrow)
        if (breakpoint != Breakpoint.ULTRA_NARROW && navigationChrome.isNotBlank()) {
            val navClipped = TerminalText.ellipsize(navigationChrome, innerWidth)
            lines.add(bColor(borderStyle.vertical.toString()) + " " + nColor(navClipped.padEnd(innerWidth)) + " " + bColor(borderStyle.vertical.toString()))
            lines.add(bColor("${borderStyle.tLeft}${borderStyle.horizontal.toString().repeat(safeWidth - 2)}${borderStyle.tRight}"))
        }

        // 3. Content Body
        for (line in contentLines) {
            val formatted = TerminalText.ellipsize(line, innerWidth)
            lines.add(bColor(borderStyle.vertical.toString()) + " " + formatted.padEnd(innerWidth) + " " + bColor(borderStyle.vertical.toString()))
        }

        // 4. Bottom Border
        val bottomBorder = bColor("${borderStyle.bottomLeft}${borderStyle.horizontal.toString().repeat(safeWidth - 2)}${borderStyle.bottomRight}")
        lines.add(bottomBorder)

        return lines
    }

    fun renderTable(
        headers: List<String>,
        rows: List<List<String>>,
        width: Int,
        borderStyle: BorderStyle = BorderStyle.ROUNDED
    ): List<String> {
        val safeWidth = width.coerceAtLeast(36)
        val columnCount = headers.size.coerceAtLeast(1)
        val colWidth = ((safeWidth - (columnCount * 3) - 1) / columnCount).coerceAtLeast(6)
        val lines = mutableListOf<String>()

        val hColor = { text: String -> BoxSemanticRoles.paint(BoxRole.HEADER, scheme, text) }
        val bColor = { text: String -> BoxSemanticRoles.paint(BoxRole.BORDER, scheme, text) }

        // Header
        val headerRow = headers.joinToString(" ${borderStyle.vertical} ") {
            hColor(TerminalText.ellipsize(it, colWidth).padEnd(colWidth))
        }
        lines.add("${borderStyle.vertical} $headerRow ${borderStyle.vertical}")

        // Separator
        val sep = headers.joinToString("${borderStyle.cross}") {
            borderStyle.horizontal.toString().repeat(colWidth + 2)
        }
        lines.add(bColor("${borderStyle.tLeft}$sep${borderStyle.tRight}"))

        // Rows
        for (row in rows) {
            val rowStr = row.mapIndexed { idx, cell ->
                val cw = colWidth
                TerminalText.ellipsize(cell, cw).padEnd(cw)
            }.joinToString(" ${borderStyle.vertical} ")
            lines.add("${borderStyle.vertical} $rowStr ${borderStyle.vertical}")
        }

        return lines
    }
}
