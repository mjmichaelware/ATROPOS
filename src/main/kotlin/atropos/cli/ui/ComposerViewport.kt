/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Glyphs
import atropos.cli.ui.design.Role
import atropos.cli.input.CommandRegistry
import atropos.cli.input.CommandPaletteLevel

data class ComposerSnapshot(
    val line: String,
    val cursorColumn: Int,
    val lines: List<String> = listOf(line),
    val cursorRow: Int = 0
)

class ComposerViewport(
    private val theme: TerminalTheme
) {
    private var buffer = ""
    private var suggestion = ""
    private var cursor = 0
    private var mode = "ASK"
    private var paletteSelection = 0

    fun update(
        buffer: String,
        suggestion: String,
        cursor: Int,
        mode: String,
        paletteSelection: Int = 0
    ) {
        this.buffer = TerminalText.sanitize(buffer)
        this.suggestion = TerminalText.sanitize(suggestion).replace('\n', ' ')
        this.cursor = safeCursorBoundary(
            this.buffer,
            cursor.coerceIn(0, this.buffer.length)
        )
        this.mode = mode.uppercase()
        this.paletteSelection = paletteSelection.coerceAtLeast(0)
    }

    fun render(width: Int): ComposerSnapshot =
        renderMultiline(width, 4)

    /**
     * Composer in the pinned reference's shape: a left rail down the input with
     * two columns of padding, and a `╹` rail terminator closing it off.
     *
     * The reference has no `[mode] >` prompt prefix — the input line carries
     * only the text, and mode/provider live on a meta row beneath, which is
     * what [metaRow] renders. Cursor arithmetic accounts for the rail so the
     * caret still lands on the right cell.
     */
    fun renderMultiline(width: Int, maximumLines: Int): ComposerSnapshot {
        val safeWidth = width.coerceAtLeast(1)
        val limit = maximumLines.coerceAtLeast(1)

        val railGlyph = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
        val rail = theme.paint(Role.ACCENT_FOCUS, railGlyph)
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val prefixCells = railGlyph.length + Glyphs.RAIL_PADDING
        val innerWidth = (safeWidth - prefixCells).coerceAtLeast(1)

        val before = buffer.substring(0, cursor)
        val after = buffer.substring(cursor)
        val rendered = before + theme.subdued(suggestion) + after
        val wrapped = AnsiLineWrapper.wrap(rendered, innerWidth).toMutableList()

        val cursorPosition = cursorPosition(before, innerWidth)
        var absoluteRow = cursorPosition.first
        val cursorColumn = cursorPosition.second + prefixCells

        while (wrapped.size <= absoluteRow) wrapped += ""

        val maximumStart = (wrapped.size - limit).coerceAtLeast(0)
        val start = (absoluteRow - limit + 1).coerceIn(0, maximumStart)
        val visible = wrapped.drop(start).take(limit).ifEmpty { listOf("") }
        absoluteRow -= start

        val railed = visible.map { rail + pad + it }

        return ComposerSnapshot(
            line = railed.first(),
            cursorColumn = cursorColumn.coerceIn(1, safeWidth),
            lines = railed,
            cursorRow = absoluteRow.coerceIn(0, railed.lastIndex)
        )
    }

    /**
     * Meta row beneath the composer. The reference renders
     * `Agent · model provider`; ATROPOS renders `mode · provider`, then closes
     * the composer with the reference's `╹` rail terminator.
     */
    fun metaRow(provider: String, width: Int): List<String> {
        val railGlyph = if (asciiOnly()) Glyphs.Ascii.RAIL else Glyphs.RAIL
        val terminator = if (asciiOnly()) "'" else "╹"
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val meta = theme.paint(Role.ACCENT_FOCUS, mode.lowercase()) +
            theme.subdued(" · ") + theme.metadata(provider.lowercase())
        return listOf(
            TerminalText.ellipsize(theme.paint(Role.ACCENT_FOCUS, railGlyph) + pad + meta, width),
            theme.paint(Role.ACCENT_FOCUS, terminator)
        )
    }

    private fun asciiOnly(): Boolean = !System.getenv("ATROPOS_ASCII").isNullOrBlank()

    fun mode(): String = mode

    fun commandQuery(): CommandPaletteQuery? {
        val value = buffer.trimStart()
        return value
            .takeIf {
                !it.contains(' ') &&
                    !it.contains('\n') &&
                    CommandRegistry.search(it).isNotEmpty()
            }
            ?.let {
                CommandPaletteQuery(
                    text = it,
                    selectedIndex = paletteSelection,
                    level = if (it.trim().lowercase() in setOf("?", "/?", "/help", "/usage", "help", "usage")) {
                        CommandPaletteLevel.GROUPS
                    } else {
                        CommandPaletteLevel.COMMANDS
                    }
                )
            }
    }

    private fun cursorPosition(value: String, width: Int): Pair<Int, Int> {
        var row = 0
        var cells = 0
        val points = value.codePoints().toArray()

        points.forEach { point ->
            if (point == '\n'.code) {
                row++
                cells = 0
            } else {
                val character = String(Character.toChars(point))
                val size = TerminalText.cellWidth(character)
                if (cells + size > width) {
                    row++
                    cells = 0
                }
                cells += size
                if (cells == width) {
                    row++
                    cells = 0
                }
            }
        }

        return row to (cells + 1).coerceIn(1, width)
    }

    private fun safeCursorBoundary(value: String, requested: Int): Int {
        var position = requested.coerceIn(0, value.length)
        if (position in 1 until value.length &&
            Character.isLowSurrogate(value[position]) &&
            Character.isHighSurrogate(value[position - 1])
        ) {
            position--
        }
        return position
    }
}
