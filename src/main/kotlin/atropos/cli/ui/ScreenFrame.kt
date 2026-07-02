/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class ScreenFrame(
    val width: Int,
    val height: Int
) {
    val lines: Array<String> =
        Array(height) { " ".repeat(width) }

    var cursorX: Int = 1
    var cursorY: Int = height
    var showCursor: Boolean = true

    init {
        require(width > 0)
        require(height > 0)
    }

    fun setLine(index: Int, content: String) {
        if (index !in lines.indices) return

        lines[index] = AnsiLineWrapper.clipAndPad(
            content,
            width
        )
    }

    fun copyLines(): Array<String> =
        lines.copyOf()
}
