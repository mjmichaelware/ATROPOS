/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

object AnsiLineWrapper {
    private const val ESC = '\u001B'

    fun clipAndPad(value: String, width: Int): String {
        val clipped = clip(value, width)
        val padding = (
            width - TerminalText.cellWidth(clipped)
        ).coerceAtLeast(0)

        return clipped + " ".repeat(padding)
    }

    fun clip(value: String, width: Int): String {
        if (width <= 0) return ""

        val output = StringBuilder()
        var cells = 0
        var index = 0
        var containedAnsi = false

        while (index < value.length) {
            if (
                value[index] == ESC &&
                index + 1 < value.length &&
                value[index + 1] == '['
            ) {
                val end = findAnsiEnd(value, index + 2)
                if (end >= 0) {
                    output.append(
                        value,
                        index,
                        end + 1
                    )
                    containedAnsi = true
                    index = end + 1
                    continue
                }
            }

            val codePoint = value.codePointAt(index)
            val character = String(
                Character.toChars(codePoint)
            )
            val characterWidth =
                TerminalText.cellWidth(character)

            if (cells + characterWidth > width) {
                break
            }

            output.append(character)
            cells += characterWidth
            index += Character.charCount(codePoint)
        }

        if (containedAnsi) output.append("\u001B[0m")
        return output.toString()
    }

    fun wrap(value: String, width: Int): List<String> {
        if (width <= 0) return emptyList()
        if (value.isEmpty()) return listOf("")

        val result = mutableListOf<String>()

        value.lines().forEach { source ->
            var remaining = source

            if (remaining.isEmpty()) {
                result += ""
                return@forEach
            }

            while (
                TerminalText.cellWidth(remaining) >
                width
            ) {
                val segment = clip(remaining, width)
                result += segment

                val visible = TerminalText.stripAnsi(
                    segment
                )
                remaining = dropVisiblePrefix(
                    remaining,
                    visible
                )

                if (remaining == source) break
            }

            result += remaining
        }

        return result
    }

    private fun dropVisiblePrefix(
        source: String,
        visiblePrefix: String
    ): String {
        var sourceIndex = 0
        var visibleIndex = 0

        while (
            sourceIndex < source.length &&
            visibleIndex < visiblePrefix.length
        ) {
            if (
                source[sourceIndex] == ESC &&
                sourceIndex + 1 < source.length &&
                source[sourceIndex + 1] == '['
            ) {
                val end = findAnsiEnd(
                    source,
                    sourceIndex + 2
                )
                if (end >= 0) {
                    sourceIndex = end + 1
                    continue
                }
            }

            val sourcePoint =
                source.codePointAt(sourceIndex)
            val visiblePoint =
                visiblePrefix.codePointAt(
                    visibleIndex
                )

            if (sourcePoint != visiblePoint) break

            sourceIndex +=
                Character.charCount(sourcePoint)
            visibleIndex +=
                Character.charCount(visiblePoint)
        }

        return source.substring(sourceIndex)
    }

    private fun findAnsiEnd(
        value: String,
        start: Int
    ): Int {
        for (index in start until value.length) {
            if (value[index].code in 0x40..0x7E) {
                return index
            }
        }
        return -1
    }
}
