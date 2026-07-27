/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class TranscriptRenderer(
    private val theme: TerminalTheme
) {
    fun user(mode: String, prompt: String): String {
        val badge = theme.metadata("[${mode.lowercase()}]")
        return "$badge ${theme.brand("›")} ${TerminalText.sanitize(prompt)}"
    }

    fun assistantHeader(provider: String): String =
        theme.metadata("╭─ ") + theme.success(provider.lowercase()) + theme.metadata(" response")

    fun assistantBody(renderedMarkdown: String): List<String> =
        renderedMarkdown.lines().flatMap { line ->
            val clean = TerminalText.sanitize(line)
            if (clean.isEmpty()) listOf(theme.metadata("│"))
            else clean.chunked(112).map { chunk -> theme.metadata("│ ") + chunk }
        }

    fun assistantFooter(): String = theme.metadata("╰─")
    fun notice(message: String): String = theme.metadata("•") + " " + TerminalText.sanitize(message)
    fun success(message: String): String = theme.success("✓") + " " + TerminalText.sanitize(message)
    fun error(message: String): String = theme.error("✗") + " " + TerminalText.sanitize(message)
    fun activity(frame: String): String = theme.metadata("├─") + " " + theme.warning(frame)
}
