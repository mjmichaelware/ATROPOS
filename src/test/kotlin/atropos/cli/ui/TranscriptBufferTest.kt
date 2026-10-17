/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptBufferTest {
    @Test
    fun appending_while_scrolled_preserves_position_and_counts_new_output() {
        val buffer = TranscriptBuffer()
        repeat(8) { buffer.append("line-$it") }

        buffer.scrollUp(2)
        val before = buffer.visibleLines(width = 40, height = 3)
        buffer.append("incoming-1")
        buffer.append("incoming-2")

        assertFalse(buffer.isFollowingTail)
        assertEquals(2, buffer.newOutputCount)
        assertEquals(before, buffer.visibleLines(width = 40, height = 3))
    }

    @Test
    fun following_tail_clears_pending_output() {
        val buffer = TranscriptBuffer()
        repeat(8) { buffer.append("line-$it") }
        buffer.scrollUp(2)
        buffer.append("incoming")

        buffer.followTail()

        assertTrue(buffer.isFollowingTail)
        assertEquals(0, buffer.newOutputCount)
        assertTrue(buffer.visibleLines(40, 3).last() == "incoming")
    }

    @Test
    fun scrolling_back_to_tail_clears_pending_output_without_resetting_early() {
        val buffer = TranscriptBuffer()
        repeat(8) { buffer.append("line-$it") }
        buffer.scrollUp(5)
        buffer.append("incoming")

        buffer.scrollDown(1)
        assertFalse(buffer.isFollowingTail)
        assertEquals(1, buffer.newOutputCount)

        buffer.scrollDown(100)
        assertTrue(buffer.isFollowingTail)
        assertEquals(0, buffer.newOutputCount)
    }

    @Test
    fun renderTranscript_shows_tail_lines_when_following_tail() {
        val theme = TerminalTheme(atropos.cli.config.ConfigurationManager(envProvider = { null }, hasConsole = false))
        val renderer = TranscriptRenderer(theme)
        val buffer = TranscriptBuffer()
        repeat(50) { buffer.append("message-$it") }

        val visible = renderer.renderTranscript(buffer, width = 80, height = 10)
        assertEquals(10, visible.size)
        assertTrue(visible.last().contains("message-49"), "Latest line must be visible at bottom of transcript")
        assertTrue(visible.first().contains("message-40"), "Tenth-to-last line must be at top of viewport")
        assertFalse(visible.any { it.contains("message-0") }, "Oldest lines must not be in tail viewport")
    }
}
