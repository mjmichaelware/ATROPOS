package atropos.cli.ui

/** Stable top-of-viewport header boundary backed by the existing chrome renderer. */
class StickyHeader(
    theme: TerminalTheme,
    private val chrome: StickyChromeRenderer = StickyChromeRenderer(theme)
) {
    data class Frame(val lines: List<String>) {
        val height: Int get() = lines.size
    }

    fun render(projectName: String, tabCount: Int, width: Int, isDensity: Boolean): Frame =
        Frame(chrome.render(projectName, tabCount, width, isDensity))
}
