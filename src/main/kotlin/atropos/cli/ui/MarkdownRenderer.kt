/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class MarkdownRenderer(
    private val colorEnabled: Boolean =
        System.console() != null &&
            System.getenv("NO_COLOR").isNullOrEmpty() &&
            !System.getenv("TERM").equals("dumb", ignoreCase = true)
) {
    private val ansi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val bold = Regex("\\*\\*([^*]+)\\*\\*")
    private val inlineCode = Regex("`([^`]+)`")
    private val fileReference = Regex("""([\w./-]+\.[A-Za-z0-9]+):(\d+)(?::(\d+))?""")

    fun render(source: String): String {
        val safe = ansi.replace(source.replace("\r\n", "\n").replace('\r', '\n'), "")
        val output = mutableListOf<String>()
        var language: String? = null

        safe.lines().forEach { original ->
            val trimmed = original.trimStart()

            if (trimmed.startsWith("```")) {
                if (language == null) {
                    language = trimmed.removePrefix("```").trim().ifEmpty { "code" }
                    output += style("  ${language}", "90")
                } else {
                    language = null
                }
                return@forEach
            }

            if (language != null) {
                val rendered = when {
                    language == "diff" && original.startsWith("+") -> style("  $original", "32")
                    language == "diff" && original.startsWith("-") -> style("  $original", "31")
                    else -> style("  $original", "36")
                }
                output += rendered
                return@forEach
            }

            var line = original
            if (trimmed.startsWith("#")) {
                line = style(trimmed.trimStart('#').trim(), "1;36")
            } else if (trimmed.startsWith("> ")) {
                line = style("│ ${trimmed.removePrefix("> ")}", "90")
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                line = "• " + trimmed.drop(2)
            }

            line = inlineCode.replace(line) {
                style(it.groupValues[1], "36")
            }
            line = bold.replace(line) {
                style(it.groupValues[1], "1")
            }
            line = fileReference.replace(line) {
                val column = it.groupValues.getOrNull(3)
                    ?.takeIf(String::isNotEmpty)
                    ?.let { value -> ":$value" }
                    ?: ""
                style(it.groupValues[1], "4;36") +
                    style(":${it.groupValues[2]}$column", "90")
            }
            output += line
        }

        return output.joinToString("\n").trimEnd()
    }

    private fun style(text: String, code: String): String =
        if (colorEnabled) "\u001B[${code}m$text\u001B[0m" else text
}
