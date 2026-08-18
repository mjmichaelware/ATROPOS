/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every key this interface answers to has to be visible somewhere.
 *
 * Arrow navigation, left/right group traversal, ctrl+t, ctrl+tab — all of it
 * worked and none of it was written down, so the only way to find a binding
 * was to press a key and notice something happened. An operator who never
 * guesses never gets the feature.
 */
class KeyboardDiscoverabilityTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))

    private fun plain(lines: List<String>) = lines.joinToString("\n", transform = TerminalText::stripAnsi)

    @Test
    fun the_shortcuts_panel_names_every_binding_the_session_handles() {
        val rendered = plain(ShortcutsRenderer(theme).render(72))

        // These are the bindings atropos.Main actually dispatches on. A legend
        // that advertises a key nothing listens for costs the operator a real
        // attempt before they conclude the interface is lying.
        listOf("ctrl+t", "ctrl+tab", "ctrl+r", "ctrl+c", "ctrl+d", "shift+tab", "esc", "enter")
            .forEach { key ->
                assertTrue(rendered.contains(key), "the shortcuts panel never mentions '$key':\n$rendered")
            }
    }

    @Test
    fun the_palette_says_how_to_move_through_it() {
        val legend = TerminalText.stripAnsi(
            KeyboardLegend.line(theme, KeyboardLegend.Surface.PALETTE, 72)
        )

        assertTrue(legend.contains("up down"), "vertical navigation is undocumented: $legend")
        assertTrue(legend.contains("left right"), "group traversal is undocumented: $legend")
        assertTrue(legend.contains("enter"), "no way to run the selection is shown: $legend")
    }

    @Test
    fun each_surface_offers_a_way_out() {
        KeyboardLegend.Surface.entries.forEach { surface ->
            val bindings = KeyboardLegend.bindingsFor(surface)
            assertTrue(bindings.isNotEmpty(), "$surface has no legend at all")
            assertTrue(
                bindings.any { it.keys.contains("esc") || it.keys.contains("ctrl+c") },
                "$surface offers no way to back out: $bindings"
            )
        }
    }

    @Test
    fun a_legend_never_overflows_its_row() {
        listOf(28, 40, 64, 120).forEach { width ->
            KeyboardLegend.Surface.entries.forEach { surface ->
                val line = KeyboardLegend.line(theme, surface, width)
                kotlin.test.assertEquals(
                    width,
                    TerminalText.cellWidth(line),
                    "$surface at $width columns rendered ${TerminalText.cellWidth(line)}"
                )
            }
        }
    }

    @Test
    fun the_shortcuts_panel_fits_a_phone() {
        ShortcutsRenderer(theme).render(46).forEach { line ->
            assertTrue(
                TerminalText.cellWidth(line) <= 46,
                "a shortcuts row overflowed 46 columns: ${TerminalText.cellWidth(line)}"
            )
        }
    }
}
