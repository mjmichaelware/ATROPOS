/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role
import atropos.core.config.AtroposUnifiedConfig

/**
 * ATOM-UI-025: Interactive Settings & Configuration Modal Renderer (/settings, /config).
 *
 * Implements a standardized beautiful box framing for the global ATROPOS settings matrix:
 * - Rounded borders (╭─╮│╰╯) with centered title and branding.
 * - Persistent navigation chrome and live option cycling instructions.
 * - Scrolling window of 14 settings rows centered around the selected index.
 * - Real-time active option highlighting with allowed choices list.
 * - Device-agnostic layout adapting to mobile, CLI, and Web Cockpit.
 */
class SettingsModalRenderer(
    private val theme: TerminalTheme
) {
    fun render(
        unifiedConfig: AtroposUnifiedConfig,
        selectedIndex: Int,
        width: Int = 80,
        visibleWindowSize: Int = 14
    ): List<String> {
        val safeWidth = width.coerceAtLeast(60)
        val innerWidth = safeWidth - 4
        val lines = mutableListOf<String>()

        // 1. Box Top Border with Centered Title
        val title = " ATROPOS CLI — Configuration Matrix (atropos.config.json) "
        lines.add(renderBoxHeader(title, safeWidth))

        // 2. Navigation Chrome Always at the Top
        val nav = "Keyboard: ↑/↓ Navigate  ←/→ Change Option  enter Save & Close  esc Exit"
        lines.add("│ " + theme.paint(Role.TEXT_MUTED, nav.padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        // 3. Helpful Hint Banner
        val hint = "Hint: Left/Right arrows cycle settings in real time; Enter commits to disk with evidence receipt"
        lines.add("│ " + theme.subdued(TerminalText.ellipsize(hint, innerWidth).padEnd(innerWidth)) + " │")
        lines.add("├" + "─".repeat(safeWidth - 2) + "┤")

        // 4. Extract all settings entries
        val allEntries = AtroposUnifiedConfig.ALLOWED_OPTIONS.keys.toList()
        if (allEntries.isEmpty()) {
            lines.add("│ " + theme.subdued("No settings declared".padEnd(innerWidth)) + " │")
            lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
            return lines
        }

        val clampedIndex = selectedIndex.coerceIn(0, allEntries.lastIndex)

        // Calculate scrolling window
        val halfWindow = visibleWindowSize / 2
        val startIdx = (clampedIndex - halfWindow).coerceAtLeast(0)
            .coerceAtMost((allEntries.size - visibleWindowSize).coerceAtLeast(0))
        val endIdx = (startIdx + visibleWindowSize).coerceAtMost(allEntries.size)

        for (i in startIdx until endIdx) {
            val key = allEntries[i]
            val isSelected = i == clampedIndex
            val cursor = if (isSelected) "❯ " else "  "

            val keyCol = if (isSelected) {
                theme.paint(Role.ACCENT_FOCUS, key.padEnd(24))
            } else {
                theme.paint(Role.TEXT_PRIMARY, key.padEnd(24))
            }

            val currentVal = unifiedConfig.get(key)
            val valCol = if (isSelected) {
                theme.strong(theme.paint(Role.ACCENT_FOCUS, "[ $currentVal ]"))
            } else {
                theme.paint(Role.BRAND, "[ $currentVal ]")
            }

            val allowedList = AtroposUnifiedConfig.ALLOWED_OPTIONS[key] ?: emptyList()
            val optionsStr = allowedList.joinToString(" | ") { opt ->
                if (opt.equals(currentVal, ignoreCase = true)) "*$opt*" else opt
            }
            val optionsFormatted = theme.subdued("<$optionsStr>")

            val row = "$cursor$keyCol $valCol  $optionsFormatted"
            lines.add("│ " + TerminalText.ellipsize(row, innerWidth).padEnd(innerWidth) + " │")
        }

        // 5. Scroll indicators if needed
        if (startIdx > 0 || endIdx < allEntries.size) {
            val scrollInfo = "  [ Showing ${startIdx + 1}..$endIdx of ${allEntries.size} settings ]"
            lines.add("│ " + theme.subdued(scrollInfo.padEnd(innerWidth)) + " │")
        }

        // 6. Box Bottom Border
        lines.add("╰" + "─".repeat(safeWidth - 2) + "╯")
        return lines
    }

    private fun renderBoxHeader(title: String, width: Int): String {
        val cleanTitle = " $title "
        val rem = (width - cleanTitle.length - 2).coerceAtLeast(2)
        val left = rem / 2
        val right = rem - left
        return "╭" + "─".repeat(left) + cleanTitle + "─".repeat(right) + "╮"
    }
}
