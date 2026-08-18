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

    /** The rows that carry cloth: everything between the blank margins. */
    private fun cloth(width: Int, height: Int) =
        plain(width, height).filter { it.isNotBlank() }

    @Test
    fun it_occupies_exactly_the_height_it_was_given() {
        listOf(46 to 8, 80 to 14, 120 to 20).forEach { (width, height) ->
            val rows = plain(width, height)
            assertEquals(height, rows.size, "wrong row count at ${width}x$height")
            rows.forEach { row ->
                assertTrue(row.length <= width, "a row overran $width cells at ${row.length}")
            }
        }
    }

    @Test
    fun the_artwork_is_inset_rather_than_bleeding_off_every_edge() {
        // A field that runs edge to edge reads as the terminal having been
        // repainted rather than as a piece of artwork on the screen. The eye
        // needs somewhere for the composition to stop.
        val rows = plain(80, 12)

        assertTrue(rows.first().isBlank(), "no blank row above the cloth")
        assertTrue(rows.last().isBlank(), "no blank row below the cloth")
        cloth(80, 12).forEach { row ->
            assertTrue(row.startsWith("  "), "the cloth starts flush against the left edge: '$row'")
            assertTrue(row.length <= 80 - 2, "the cloth reaches the right edge: ${row.length}")
        }
        // Centred, so the panel sits on the screen rather than hanging off one
        // side of it.
        val indents = cloth(80, 12).map { it.length - it.trimStart().length }.distinct()
        assertEquals(1, indents.size, "the rows are not aligned with each other: $indents")
    }

    @Test
    fun the_weave_has_structure_rather_than_noise() {
        // Warp threads at a regular interval are the difference between cloth
        // and static.
        val rows = cloth(80, 16)
        val columns = rows.minOf { it.length }
        val warpColumns = (0 until columns).filter { column ->
            rows.all { it[column] == WARP || it[column] == CROSS }
        }

        assertTrue(warpColumns.size >= 8, "no regular warp threads: found ${warpColumns.size}")
        val gaps = warpColumns.zipWithNext { a, b -> b - a }.distinct()
        assertEquals(1, gaps.size, "warp threads are not evenly spaced: $gaps")
    }

    @Test
    fun the_field_between_the_threads_is_empty() {
        // It is a background. Every mark it adds is a mark the eye has to rule
        // out before it finds the prompt, and both a density ramp and a
        // scattering of dots put a picture in front of the thing the picture
        // was meant to sit behind.
        val glyphs = cloth(80, 16).flatMap { it.trim().toList() }.toSet()

        assertEquals(setOf(WARP, WEFT, CROSS, ' '), glyphs, "the field carries marks of its own")
    }

    @Test
    fun the_lattice_closes_on_a_thread_at_every_edge() {
        // A weave cut off mid-cell looks like a mistake; one that finishes
        // looks chosen.
        val rows = cloth(80, 16)

        rows.forEach { row ->
            val line = row.trim()
            assertTrue(line.first() in setOf(WARP, CROSS), "the left edge is not a thread: '$line'")
            assertTrue(line.last() in setOf(WARP, CROSS), "the right edge is not a thread: '$line'")
        }
        assertTrue(rows.first().contains(CROSS), "the top row is not a weft thread")
        assertTrue(rows.last().contains(CROSS), "the bottom row is not a weft thread")
    }

    @Test
    fun warp_and_weft_actually_cross() {
        assertTrue(
            cloth(80, 16).any { it.contains(CROSS) },
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
        // Wide enough for cloth, but not once the margins are taken out.
        assertTrue(tapestry.render(26, 8).isEmpty())
        assertTrue(tapestry.render(80, 2).isEmpty())
    }

    @Test
    fun colour_is_emitted_only_where_it_changes() {
        // A per-cell escape sequence makes one row of a wide terminal several
        // kilobytes, and the diffing renderer rewrites all of it every frame.
        ThreadTapestry(TerminalTheme(ConfigurationManager(envProvider = { null }, hasConsole = true)))
            .render(120, 6)
            .forEach { row ->
                // Fewer than one per cell. Colour changes at thread crossings
                // and where the sheen steps, not at every column.
                val escapes = row.count { it == ESCAPE }
                assertTrue(escapes < 120, "a 120-cell row carried $escapes escape sequences")
            }
    }

    private companion object {
        const val WARP = '\u2502'
        const val WEFT = '\u2500'
        const val CROSS = '\u253C'
        const val ESCAPE = '\u001B'
    }
}
