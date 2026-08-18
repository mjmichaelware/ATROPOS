package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnimatedThinkingBufferTest {
    @Test
    fun frame_sequence_is_deterministic_and_wraps_without_clock_input() {
        val buffer = AnimatedThinkingBuffer(listOf(".", "..", "..."))

        // Two spaces between the indicator and the message: the pulse is a
        // block bar now, and one space ran the message straight into it.
        assertEquals(
            listOf(".  Thinking", "..  Thinking", "...  Thinking", ".  Thinking"),
            buffer.sequence(4, "Thinking")
        )
        assertEquals("...  Thinking", buffer.render(-1, "Thinking"))
    }

    @Test
    fun blank_messages_and_empty_sequences_are_refused() {
        val buffer = AnimatedThinkingBuffer(listOf("."))

        assertFailsWith<IllegalArgumentException> { buffer.render(0, " ") }
        assertEquals(emptyList(), buffer.sequence(0, "Thinking"))
    }
}
