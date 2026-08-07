/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * HOE-B01: Renders sticky chrome bar at top of viewport.
 * Project name + status remain visible during resize.
 * Compact form for narrow terminals (<80 chars).
 */
class StickyChromeRenderer(private val theme: TerminalTheme) {
    fun render(projectName: String, tabCount: Int, width: Int, isDensity: Boolean): List<String> {
        val safeWidth = width.coerceAtLeast(20)
        val name = projectName.take(safeWidth - 10).padEnd(safeWidth - 10)
        val meta = "[$tabCount tab${if (tabCount > 1) "s" else ""}]"

        return if (isDensity) {
            // Compact: single line
            listOf("▌ $name $meta".take(safeWidth))
        } else {
            // Comfortable: two lines
            listOf(
                "▌ $name".take(safeWidth),
                "  $meta".take(safeWidth)
            )
        }
    }
}
