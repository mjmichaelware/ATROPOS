package atropos.cli.commands

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.design.Role

object AgentCommandText {
    private val theme = TerminalTheme(ConfigurationManager())
    private val surface get() = theme.surface

    fun formatBlock(title: String, body: String): String {
        val width = 80
        val lines = body.lineSequence().flatMap { wrapLine(it, width - 4).lineSequence() }.toList()
        return surface.block(title.uppercase(), lines, width, Role.BRAND).joinToString("\n")
    }

    fun renderRendererOutput(lines: List<String>): String =
        lines.joinToString("\n").trimEnd()

    // Only very long unbroken lines are pre-wrapped; the reactive renderer
    // already wraps transcript lines at the live terminal width.
    private fun wrapLine(line: String, width: Int = 320): String {
        if (line.length <= width) return line
        val leading = line.takeWhile { it == ' ' }
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return line

        val available = (width - leading.length).coerceAtLeast(10)
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > available) {
                segments += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) segments += current.toString()

        return leading + segments.joinToString("\n$leading  ")
    }
}
