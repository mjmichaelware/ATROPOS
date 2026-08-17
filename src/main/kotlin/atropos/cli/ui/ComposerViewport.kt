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
    private val composerRenderer = ComposerRenderer()
    private val modeRetheme = ModeRetheme()
    private var buffer = ""
    private var suggestion = ""
    private var cursor = 0
    private var mode = "ASK"
    private var paletteSelection = 0
    private var paletteLevel = CommandPaletteLevel.COMMANDS
    private var paletteGroup: String? = null
    private var paletteCommand: String? = null

    fun update(
        buffer: String,
        suggestion: String,
        cursor: Int,
        mode: String,
        paletteSelection: Int = 0,
        paletteLevel: CommandPaletteLevel = CommandPaletteLevel.COMMANDS,
        paletteGroup: String? = null,
        paletteCommand: String? = null
    ) {
        val prepared = composerRenderer.prepare(
            buffer = buffer,
            suggestion = suggestion,
            cursor = cursor,
            mode = mode,
            terminalWidth = Int.MAX_VALUE
        )
        this.buffer = prepared.buffer
        this.suggestion = prepared.suggestion
        this.cursor = safeCursorBoundary(this.buffer, prepared.cursor)
        this.mode = prepared.mode
        this.paletteSelection = paletteSelection.coerceAtLeast(0)
        this.paletteLevel = paletteLevel
        this.paletteGroup = paletteGroup
        this.paletteCommand = paletteCommand
    }

    fun render(width: Int): ComposerSnapshot =
        renderMultiline(width, 4)

    /**
     * The composer as an enclosed box, not a rail.
     *
     * The input line used to be drawn with a left rail and nothing else, which
     * put it on the same visual plane as the transcript scrolling above it: an
     * operator looking at the screen could not tell what they had typed from
     * what the engine had printed back, because both were text starting two
     * columns in. A closed border is the cheapest thing that says "this region
     * is yours" — the top edge is drawn here as the first snapshot line and the
     * bottom edge by [metaRow], so the two together enclose the input and the
     * height arithmetic in `ViewportLayout` is unchanged.
     *
     * There is no `[mode] >` prefix inside the box. Mode and provider are
     * written into the bottom border instead, where they label the region
     * rather than competing with the caret for the start of the line.
     */
    fun renderMultiline(width: Int, maximumLines: Int): ComposerSnapshot {
        val safeWidth = width.coerceAtLeast(1)
        val limit = maximumLines.coerceAtLeast(1)

        val edge = edge()
        val prefixCells = edge.vertical.length + Glyphs.RAIL_PADDING
        // Both borders and both pads: `│ text │`.
        val innerWidth = (safeWidth - (prefixCells * 2)).coerceAtLeast(1)

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

        val rail = theme.paint(Role.ACCENT_FOCUS, edge.vertical)
        val pad = " ".repeat(Glyphs.RAIL_PADDING)
        val boxed = visible.map { line ->
            // padEnd, never clip: clip() strips ANSI, and the suggestion in
            // this line is subdued by a colour code. The wrapper already
            // guarantees the width, so there is nothing to cut.
            rail + pad + TerminalText.padEnd(line, innerWidth) + pad + rail
        }

        // The top border occupies snapshot row 0, so every body row — and the
        // caret with it — sits one lower than the wrap arithmetic computed.
        val lines = listOf(topBorder(safeWidth, edge)) + boxed

        return ComposerSnapshot(
            line = lines.first(),
            cursorColumn = cursorColumn.coerceIn(1, safeWidth),
            lines = lines,
            cursorRow = (absoluteRow + 1).coerceIn(0, lines.lastIndex)
        )
    }

    /**
     * The composer's bottom border, with mode and provider written into it.
     *
     * A single line, because the box already separates the input from the
     * transcript and a second row of chrome beneath it would only take a row
     * away from the transcript to say the same thing twice.
     */
    fun metaRow(provider: String, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(1)
        val edge = edge()
        val modeStyle = modeRetheme.style(mode)
        val label = modeStyle.label.lowercase() + " · " + provider.lowercase()
        val painted = theme.paint(modeStyle.role, modeStyle.label.lowercase()) +
            theme.subdued(" · ") + theme.metadata(provider.lowercase())

        // `╰─ ask · groq ──────╯`: corners, one lead dash, the label, then fill.
        val decoration = 2 + edge.horizontal.length + 2
        val fill = (safeWidth - decoration - TerminalText.cellWidth(label)).coerceAtLeast(0)
        val body =
            if (fill == 0 && safeWidth > 2) {
                edge.horizontal.repeat(safeWidth - 2)
            } else {
                edge.horizontal + " " + painted + " " + edge.horizontal.repeat(fill)
            }

        return listOf(
            theme.paint(Role.ACCENT_FOCUS, edge.bottomLeft) +
                body +
                theme.paint(Role.ACCENT_FOCUS, edge.bottomRight)
        )
    }

    private fun topBorder(width: Int, edge: Edge): String =
        theme.paint(
            Role.ACCENT_FOCUS,
            edge.topLeft + edge.horizontal.repeat((width - 2).coerceAtLeast(0)) + edge.topRight
        )

    private data class Edge(
        val vertical: String,
        val horizontal: String,
        val topLeft: String,
        val topRight: String,
        val bottomLeft: String,
        val bottomRight: String
    )

    private fun edge(): Edge = if (asciiOnly()) {
        Edge(
            Glyphs.Ascii.RAIL, Glyphs.Ascii.RULE,
            Glyphs.Ascii.BOX_TOP_LEFT, Glyphs.Ascii.BOX_TOP_RIGHT,
            Glyphs.Ascii.BOX_BOTTOM_LEFT, Glyphs.Ascii.BOX_BOTTOM_RIGHT
        )
    } else {
        Edge(
            Glyphs.RAIL, Glyphs.RULE,
            Glyphs.BOX_TOP_LEFT, Glyphs.BOX_TOP_RIGHT,
            Glyphs.BOX_BOTTOM_LEFT, Glyphs.BOX_BOTTOM_RIGHT
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
                    level = paletteLevel,
                    selectedGroup = paletteGroup,
                    selectedCommand = paletteCommand
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
