/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The home screen's artwork.
 *
 * It replaced two thirds of black, and then had to be replaced itself: the
 * first version was interference waves, which read as organic noise rather
 * than as anything deliberate. Cloth has structure -- warp, weft, and the
 * crossings between them -- and structure is what makes a field read as
 * designed instead of as static.
 */
class ThreadTapestryTest {

    private val theme = TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = false))
    private val tapestry = ThreadTapestry(theme)

    private fun plain(width: Int, height: Int) =
        tapestry.render(width, height).map(TerminalText::stripAnsi)

    @Test
    fun it_fills_exactly_the_space_it_was_given() {
        listOf(46 to 8, 80 to 14, 120 to 20).forEach { (width, height) ->
            val rows = plain(width, height)
            assertEquals(height, rows.size, "wrong row count at ${width}x$height")
            rows.forEach { row ->
                assertEquals(width, row.length, "a row was $width wide but rendered ${row.length}")
            }
        }
    }

    @Test
    fun the_weave_has_structure_rather_than_noise() {
        // Warp threads at a regular interval are the difference between cloth
        // and static.
        val rows = plain(80, 12)
        val warpColumns = (0 until 80).filter { column ->
            rows.all { it[column] == WARP || it[column] == CROSS }
        }

        assertTrue(warpColumns.size >= 8, "no regular warp threads: found ${warpColumns.size}")
        val gaps = warpColumns.zipWithNext { a, b -> b - a }.distinct()
        assertEquals(1, gaps.size, "warp threads are not evenly spaced: $gaps")
    }

    @Test
    fun warp_and_weft_actually_cross() {
        assertTrue(
            plain(80, 12).any { it.contains(CROSS) },
            "the threads never cross, so it is a grid of lines and not a weave"
        )
    }

    @Test
    fun it_is_deterministic() {
        // A home screen that shimmered differently on every repaint would pull
        // the eye off the prompt, and an undrawable frame could never be
        // reproduced from a bug report.
        assertEquals(plain(80, 10), plain(80, 10))
    }

    @Test
    fun a_terminal_too_narrow_gets_nothing_rather_than_a_smear() {
        assertTrue(tapestry.render(12, 8).isEmpty())
        assertTrue(tapestry.render(80, 0).isEmpty())
    }

    @Test
    fun colour_is_emitted_only_where_it_changes() {
        // A per-cell escape sequence makes one row of a wide terminal several
        // kilobytes, and the diffing renderer rewrites all of it every frame.
        ThreadTapestry(TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = true)))
            .render(120, 4)
            .forEach { row ->
                // Fewer than one per cell. Colour changes at thread crossings
                // and where the sheen steps, not at every column.
                val escapes = row.count { it == ESCAPE }
                assertTrue(escapes < 120, "a 120-cell row carried $escapes escape sequences")
            }
    }

    private companion object {
        const val WARP = '\u2502'
        const val CROSS = '\u253C'
        const val ESCAPE = '\u001B'
    }
}
