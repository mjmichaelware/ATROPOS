/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * The keyboard, on one screen.
 *
 * Reads from [KeyboardLegend] rather than holding its own list, so the panel
 * and the inline hints can never disagree about what a key does — and so a
 * binding is documented in exactly one place.
 */
class ShortcutsRenderer(private val theme: TerminalTheme) {

    fun render(width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(20)

        // Aligned on the widest key across every group, so the actions form one
        // column down the whole panel instead of a new column per section.
        val keyColumn = KeyboardLegend.all()
            .flatMap { (_, bindings) -> bindings }
            .maxOf { it.keys.length }
            .coerceAtMost(MAXIMUM_KEY_CELLS)

        return buildList {
            add(theme.paint(Role.BRAND, " Keyboard shortcuts"))
            add("")

            KeyboardLegend.all().forEach { (section, bindings) ->
                add(theme.subdued(" " + section.uppercase()))
                bindings.forEach { binding ->
                    add(
                        TerminalText.ellipsize(
                            "  " +
                                theme.paint(Role.ACCENT_FOCUS, TerminalText.padEnd(binding.keys, keyColumn)) +
                                "  " + theme.strong(binding.action),
                            safeWidth
                        )
                    )
                }
                add("")
            }

            add(KeyboardLegend.line(theme, KeyboardLegend.Surface.COMPOSER, safeWidth))
        }
    }

    private companion object {
        const val MAXIMUM_KEY_CELLS = 16
    }
}
