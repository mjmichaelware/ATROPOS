/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class HeaderRenderer(
    private val theme: TerminalTheme
) {
    fun render(state: SessionPresentationState, width: Int): String {
        val safeWidth = width.coerceAtLeast(1)
        val brand = " ATROPOS "
        val provider = state.provider.lowercase()
        val mode = state.mode.lowercase()
        val operation = state.activeOperation
            ?.let(TerminalText::sanitize)
            ?.takeIf(String::isNotBlank)
            ?.let { " · $it" }
            ?: ""

        val right = listOf(
            "$provider · $mode$operation · /help ",
            "$provider · $mode · /help ",
            "$provider · /help ",
            "/help "
        ).firstOrNull {
            TerminalText.cellWidth(brand) + TerminalText.cellWidth(it) <= safeWidth
        }.orEmpty()

        val gap = (
            safeWidth -
                TerminalText.cellWidth(brand) -
                TerminalText.cellWidth(right)
        ).coerceAtLeast(0)

        return theme.headerBrand(brand) +
            theme.headerText(" ".repeat(gap) + right)
    }
}
