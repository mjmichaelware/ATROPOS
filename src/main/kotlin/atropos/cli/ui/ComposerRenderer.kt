/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

data class ComposerFrame(
    val buffer: String,
    val suggestion: String,
    val cursor: Int,
    val mode: String
)

class ComposerRenderer {
    private val supportedModes = setOf("ASK", "PLAN", "AUTOPILOT")

    fun prepare(
        buffer: String,
        suggestion: String,
        cursor: Int,
        mode: String,
        terminalWidth: Int
    ): ComposerFrame {
        val safeBuffer = TerminalText.sanitize(buffer)
            .replace('\n', ' ')
        val safeMode = mode.uppercase()
            .takeIf(supportedModes::contains)
            ?: "ASK"
        val safeCursor = clampCursor(safeBuffer, cursor)
        val prefixWidth = TerminalText.cellWidth(
            "[${safeMode.lowercase()}] › "
        )
        val available = (
            terminalWidth.coerceAtLeast(36) -
                prefixWidth -
                TerminalText.cellWidth(safeBuffer)
            ).coerceAtLeast(0)
        val safeSuggestion = TerminalText.clip(
            TerminalText.sanitize(suggestion).replace('\n', ' '),
            available
        )

        return ComposerFrame(
            buffer = safeBuffer,
            suggestion = safeSuggestion,
            cursor = safeCursor,
            mode = safeMode
        )
    }

    private fun clampCursor(text: String, requested: Int): Int {
        var position = requested.coerceIn(0, text.length)

        if (
            position in 1 until text.length &&
            Character.isLowSurrogate(text[position]) &&
            Character.isHighSurrogate(text[position - 1])
        ) {
            position--
        }

        return position
    }
}
