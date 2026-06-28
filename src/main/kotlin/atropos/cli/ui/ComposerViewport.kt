/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

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

    fun renderMultiline(width: Int, maximumLines: Int): ComposerSnapshot {
        val safeWidth = width.coerceAtLeast(1)
        val limit = maximumLines.coerceAtLeast(1)
        val badge = "[${mode.lowercase()}] "
        val prompt = "> "
        val prefixPlain = badge + prompt
        val before = buffer.substring(0, cursor)
        val after = buffer.substring(cursor)
        val rendered = theme.metadata(badge) + theme.brand(prompt) +
            before + theme.metadata(suggestion) + after
        val wrapped = AnsiLineWrapper.wrap(rendered, safeWidth).toMutableList()
        val cursorPosition = cursorPosition(
            prefixPlain + before,
            safeWidth
        )
        var absoluteRow = cursorPosition.first
        val cursorColumn = cursorPosition.second

        while (wrapped.size <= absoluteRow) {
            wrapped += ""
        }

        val maximumStart = (wrapped.size - limit).coerceAtLeast(0)
        val start = (absoluteRow - limit + 1).coerceIn(0, maximumStart)
        val visible = wrapped.drop(start).take(limit).ifEmpty { listOf("") }
        absoluteRow -= start

        return ComposerSnapshot(
            line = visible.first(),
            cursorColumn = cursorColumn.coerceIn(1, safeWidth),
            lines = visible,
            cursorRow = absoluteRow.coerceIn(0, visible.lastIndex)
        )
    }

    fun mode(): String = mode

    fun commandQuery(): CommandPaletteQuery? {
        val value = buffer.trimStart()
        return value
            .takeIf {
                it.startsWith("/") &&
                    !it.contains(' ') &&
                    !it.contains('\n')
            }
            ?.let {
                CommandPaletteQuery(it, paletteSelection)
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
