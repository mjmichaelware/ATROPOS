package atropos.cli.commands

object AgentCommandText {
    fun formatBlock(title: String, body: String): String = buildString {
        appendLine("── $title ──")
        body.lineSequence().forEach { line -> append(wrapLine(line)).append('\n') }
    }.trimEnd()

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
