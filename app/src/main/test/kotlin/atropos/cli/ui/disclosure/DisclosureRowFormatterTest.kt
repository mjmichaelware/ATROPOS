/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import atropos.cli.ui.TerminalText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The formatter is the half of the disclosure seam a `TERM=dumb`, 40-column
 * Termux window has to survive: the open/closed state is never carried by the
 * twisty glyph alone, and a 400-column payload is clipped rather than left to
 * wrap through the surrounding layout.
 */
class DisclosureRowFormatterTest {

    private fun content(): DisclosureContent = DisclosureContent.of(
        summary = "thought for 2s",
        l1 = listOf("considered two options"),
        l2 = listOf("option a: patch")
    )

    private fun collapsed(): DisclosureRow =
        DisclosureRow.collapsed(DisclosureRowKind.THINKING, content())

    private val ascii40 = DisclosureRowStyle(width = 40, asciiOnly = true)

    @Test
    fun a_collapsed_row_is_exactly_one_line_of_marker_label_and_summary() {
        val row = collapsed()

        assertEquals("▸ Thinking thought for 2s", DisclosureRowFormatter.header(row))
        assertEquals(emptyList(), DisclosureRowFormatter.body(row))
        assertEquals(listOf("▸ Thinking thought for 2s"), DisclosureRowFormatter.render(row))
    }

    @Test
    fun an_open_row_states_its_depth_in_words_not_only_by_glyph() {
        val open = collapsed().expand()!!.row

        assertEquals("▾ Thinking thought for 2s  L1 of L2", DisclosureRowFormatter.header(open))
        assertEquals(listOf("  considered two options"), DisclosureRowFormatter.body(open))
    }

    @Test
    fun a_fully_open_row_uses_the_terminal_marker_so_no_dead_arrow_is_offered() {
        val deepest = collapsed().expand()!!.row.expand()!!.row

        val header = DisclosureRowFormatter.header(deepest)
        assertTrue(header.startsWith("· Thinking"), header)
        assertTrue(header.endsWith("L2"), header)
        assertFalse(header.contains("▾"), header)
        assertEquals(
            listOf("  considered two options", "  option a: patch"),
            DisclosureRowFormatter.body(deepest)
        )
    }

    @Test
    fun a_leaf_row_is_drawn_with_the_terminal_marker_and_no_body() {
        val leaf = DisclosureRow.collapsed(
            DisclosureRowKind.EVIDENCE,
            DisclosureContent.leaf("nothing cited")
        )

        assertEquals("· Evidence nothing cited", DisclosureRowFormatter.header(leaf))
        assertEquals(emptyList(), DisclosureRowFormatter.body(leaf))
    }

    @Test
    fun ascii_only_terminals_get_ascii_markers_and_an_ascii_ellipsis() {
        val open = collapsed().expand()!!.row

        val header = DisclosureRowFormatter.header(open, ascii40)
        assertEquals("v Thinking thought for 2s", header)
        assertTrue(header.all { it.code < 128 }, header)
        assertEquals("...", ascii40.ellipsis)
        assertEquals("…", DisclosureRowStyle.DEFAULT.ellipsis)
    }

    @Test
    fun every_line_fits_the_forty_column_window_and_stays_ascii() {
        val wide = DisclosureContent.of(
            summary = "a summary long enough that it cannot possibly fit a phone terminal row",
            l1 = listOf("x".repeat(400)),
            l2 = listOf("y".repeat(120))
        )
        var row = DisclosureRow.collapsed(DisclosureRowKind.ENGINE, wide)
        row = row.expand()!!.row
        row = row.expand()!!.row

        val lines = DisclosureRowFormatter.render(row, ascii40)

        assertEquals(3, lines.size)
        lines.forEach { line ->
            assertTrue(TerminalText.cellWidth(line) <= 40, "overflowed: ${line.length} cells in $line")
            assertTrue(line.all { it.code < 128 }, line)
        }
        assertTrue(lines[0].startsWith(". Engine "), lines[0])
        assertTrue(lines[1].endsWith("..."), lines[1])
        assertTrue(lines[2].endsWith("..."), lines[2])
    }

    @Test
    fun a_compact_terminal_drops_the_level_suffix_before_it_drops_the_label() {
        val open = collapsed().expand()!!.row

        val compact = DisclosureRowFormatter.header(open, DisclosureRowStyle(width = 40))
        assertFalse(compact.contains("L1"), compact)
        assertTrue(compact.contains("Thinking"), compact)

        val medium = DisclosureRowFormatter.header(open, DisclosureRowStyle(width = 80))
        assertTrue(medium.contains("L1 of L2"), medium)
    }

    @Test
    fun a_window_too_narrow_for_orientation_text_keeps_the_label_alone() {
        val header = DisclosureRowFormatter.header(collapsed(), DisclosureRowStyle(width = 12))

        assertEquals("▸ Thinking", header)
    }

    @Test
    fun the_indent_shifts_the_header_and_indents_the_body_beneath_it() {
        val open = collapsed().expand()!!.row
        val railed = DisclosureRowStyle(width = 80, indent = 4)

        assertTrue(
            DisclosureRowFormatter.header(open, railed).startsWith("    ▸") ||
                DisclosureRowFormatter.header(open, railed).startsWith("    ▾"),
            DisclosureRowFormatter.header(open, railed)
        )
        assertEquals(
            listOf("      considered two options"),
            DisclosureRowFormatter.body(open, railed)
        )
    }

    @Test
    fun the_streaming_path_emits_only_the_lines_the_expand_added() {
        val first = collapsed().expand()!!
        val second = first.row.expand()!!

        assertEquals(
            listOf("  considered two options"),
            DisclosureRowFormatter.addedLines(first.reveal)
        )
        assertEquals(
            listOf("  option a: patch"),
            DisclosureRowFormatter.addedLines(second.reveal)
        )
    }

    @Test
    fun the_repaint_path_and_the_streaming_path_agree_on_the_visible_body() {
        val first = collapsed().expand()!!
        val second = first.row.expand()!!

        assertEquals(
            DisclosureRowFormatter.body(second.row),
            DisclosureRowFormatter.addedLines(first.reveal) +
                DisclosureRowFormatter.addedLines(second.reveal)
        )
    }

    @Test
    fun colour_arriving_inside_content_is_stripped_rather_than_forwarded() {
        val coloured = DisclosureContent.of(
            summary = "coloured payload",
            l1 = listOf("\u001B[31mred text\u001B[0m", "tab\there")
        )
        val row = DisclosureRow.collapsed(DisclosureRowKind.EVIDENCE, coloured).expand()!!.row

        assertEquals(listOf("  red text", "  tab here"), DisclosureRowFormatter.body(row))
        assertFalse(DisclosureRowFormatter.render(row).any { it.contains('\u001B') })
    }

    @Test
    fun a_misconfigured_width_is_floored_instead_of_producing_negative_room() {
        val style = DisclosureRowStyle(width = 2, indent = 8)

        assertEquals(DisclosureRowStyle.MINIMUM_CONTENT, style.contentWidth)
        assertEquals(
            DisclosureRowStyle.MINIMUM_CONTENT,
            DisclosureRowStyle(width = 80, indent = 200).contentWidth
        )
        assertTrue(DisclosureRowFormatter.body(collapsed().expand()!!.row, style).isNotEmpty())
    }

    @Test
    fun the_marker_says_open_closed_or_nothing_deeper_and_never_offers_a_dead_arrow() {
        assertEquals(
            DisclosureMarker.COLLAPSED,
            DisclosureMarker.of(DisclosureState.Collapsed, canExpand = true)
        )
        assertEquals(
            DisclosureMarker.TERMINAL,
            DisclosureMarker.of(DisclosureState.Collapsed, canExpand = false)
        )
        assertEquals(
            DisclosureMarker.EXPANDED,
            DisclosureMarker.of(DisclosureState.Expanded(DisclosureLevel.L1), canExpand = true)
        )
        assertEquals(
            DisclosureMarker.TERMINAL,
            DisclosureMarker.of(DisclosureState.Expanded(DisclosureLevel.L4), canExpand = false)
        )
    }

    @Test
    fun every_marker_has_a_single_width_ascii_fallback() {
        DisclosureMarker.entries.forEach { marker ->
            assertEquals(1, TerminalText.cellWidth(marker.asciiGlyph), marker.name)
            assertEquals(marker.asciiGlyph, marker.resolve(asciiOnly = true))
            assertEquals(marker.glyph, marker.resolve(asciiOnly = false))
            assertTrue(marker.glyph.isNotBlank(), marker.name)
        }
    }
}
