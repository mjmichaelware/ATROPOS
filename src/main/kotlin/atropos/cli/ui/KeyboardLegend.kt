/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * What the keyboard does here, said out loud.
 *
 * Every navigation key this interface answers to was invisible. Arrow keys
 * moved through the palette, left and right crossed between command groups,
 * ctrl+t opened a tab and ctrl+tab cycled them — all of it worked, and the
 * only way to find out was to press a key and notice something happened. An
 * operator who never guesses never gets the feature.
 *
 * One owner rather than a hint string at each surface, so the palette, the
 * shortcuts panel and the home screen cannot drift into describing the same
 * key three different ways — and so a binding that changes in
 * [atropos.Main] has exactly one place to be corrected.
 *
 * Every binding named here is one the input loop actually handles. A legend
 * that advertises a key nothing listens for is worse than no legend: it costs
 * the operator a real attempt before they conclude the interface is lying.
 */
object KeyboardLegend {

    /** Where a legend is being drawn, which decides what is worth saying. */
    enum class Surface { PALETTE, GROUPS, DETAIL, COMPOSER }

    data class Binding(val keys: String, val action: String)

    fun bindingsFor(surface: Surface): List<Binding> = when (surface) {
        Surface.PALETTE -> listOf(
            Binding("up down", "select"),
            Binding("left right", "group"),
            Binding("enter", "run"),
            Binding("tab", "complete"),
            Binding("esc", "close")
        )
        Surface.GROUPS -> listOf(
            Binding("up down", "group"),
            Binding("right", "open"),
            Binding("enter", "run"),
            Binding("esc", "close")
        )
        Surface.DETAIL -> listOf(
            Binding("left", "back"),
            Binding("enter", "run"),
            Binding("esc", "close")
        )
        Surface.COMPOSER -> listOf(
            Binding("/", "commands"),
            Binding("@", "attach"),
            Binding("ctrl+t", "new tab"),
            Binding("ctrl+r", "history"),
            Binding("ctrl+c", "cancel")
        )
    }

    /**
     * Every binding the session answers to, for the shortcuts panel.
     *
     * Grouped the way an operator looks for them — by what they are trying to
     * do, not by which key happens to be pressed.
     */
    fun all(): List<Pair<String, List<Binding>>> = listOf(
        "Editing" to listOf(
            Binding("left right", "move the caret"),
            Binding("home end", "start / end of line"),
            Binding("backspace", "delete back"),
            Binding("delete", "delete forward"),
            Binding("enter", "send")
        ),
        "Commands" to listOf(
            Binding("/", "open the command palette"),
            Binding("up down", "move through results"),
            Binding("left right", "move between groups"),
            Binding("tab", "complete the selection"),
            Binding("shift+tab", "previous suggestion"),
            Binding("esc", "close the palette")
        ),
        "Files" to listOf(
            Binding("@", "attach a file by path"),
            Binding("tab", "complete the path")
        ),
        "History" to listOf(
            Binding("up down", "previous / next prompt"),
            Binding("ctrl+r", "search history")
        ),
        "Tabs" to listOf(
            Binding("ctrl+t", "open a new tab"),
            Binding("ctrl+tab", "cycle to the next tab"),
            Binding("/tabs", "list open tabs"),
            Binding("/tab close <n>", "close a tab")
        ),
        "Session" to listOf(
            Binding("ctrl+c", "cancel what is running"),
            Binding("ctrl+d", "exit"),
            Binding("/help", "every command"),
            Binding("/shortcuts", "this panel")
        )
    )

    /**
     * A legend rendered as one line, padded to [width].
     *
     * Ellipsized rather than wrapped, because the legend is a reminder and a
     * reminder that costs a second row is competing with the thing it is
     * reminding you about.
     */
    fun line(theme: TerminalTheme, surface: Surface, width: Int, indent: Int = 1): String {
        val body = bindingsFor(surface).joinToString(theme.subdued("  ")) { binding ->
            theme.paint(Role.ACCENT_FOCUS, binding.keys) + theme.subdued(" " + binding.action)
        }
        val padded = " ".repeat(indent.coerceAtLeast(0)) + body
        return TerminalText.padEnd(
            TerminalText.ellipsize(padded, width.coerceAtLeast(1)),
            width.coerceAtLeast(1)
        )
    }
}
