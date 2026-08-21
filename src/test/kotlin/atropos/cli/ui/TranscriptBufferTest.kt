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
}
