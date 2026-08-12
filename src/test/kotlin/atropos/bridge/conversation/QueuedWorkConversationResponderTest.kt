/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueuedWorkConversationResponderTest {

    private class RecordingQueue(private val id: String = "queue-1") : ConversationWorkQueue {
        val accepted = mutableListOf<String>()
        override fun enqueue(task: String): String {
            accepted += task
            return id
        }
    }

    @Test
    fun a_plain_language_message_becomes_queued_work_and_the_reply_names_the_record() {
        val queue = RecordingQueue("queue-42")
        val reply = QueuedWorkConversationResponder(queue).reply("build me a chapter tracker")

        assertEquals(listOf("build me a chapter tracker"), queue.accepted)
        assertTrue(reply.contains("queue-42"), "the operator must be able to follow the work")
    }

    @Test
    fun surrounding_whitespace_is_not_part_of_the_task() {
        val queue = RecordingQueue()
        QueuedWorkConversationResponder(queue).reply("   trim me   ")

        assertEquals(listOf("trim me"), queue.accepted)
    }

    /**
     * The command surface belongs to the CLI router. Executing it here would
     * mean a second implementation that could disagree with the first.
     */
    @Test
    fun a_slash_command_is_refused_rather_than_reimplemented() {
        val queue = RecordingQueue()
        val reply = QueuedWorkConversationResponder(queue).reply("/status")

        assertTrue(queue.accepted.isEmpty(), "a command must not be silently queued as prose")
        assertTrue(reply.contains("CLI surface"))
    }

    @Test
    fun an_empty_message_queues_nothing() {
        val queue = RecordingQueue()
        val reply = QueuedWorkConversationResponder(queue).reply("    ")

        assertTrue(queue.accepted.isEmpty())
        assertTrue(reply.contains("empty"))
    }

    @Test
    fun an_oversized_message_is_refused_with_both_sizes_stated() {
        val queue = RecordingQueue()
        val reply = QueuedWorkConversationResponder(queue, maxTaskChars = 50)
            .reply("x".repeat(120))

        assertTrue(queue.accepted.isEmpty())
        assertTrue(reply.contains("120"))
        assertTrue(reply.contains("50"))
    }

    /**
     * A policy-gate refusal surfaces as an exception from enqueue. It is the
     * operator's answer, so it must reach them rather than becoming a generic
     * failure or propagating out of the HTTP handler.
     */
    @Test
    fun a_refusal_from_the_queue_is_reported_as_the_reply() {
        val refusing = ConversationWorkQueue { error("paid provider is locked") }
        val reply = QueuedWorkConversationResponder(refusing).reply("spend money please")

        assertTrue(reply.contains("did not accept"))
        assertTrue(reply.contains("paid provider is locked"))
    }

    @Test
    fun a_failure_with_no_message_still_produces_a_usable_reply() {
        val refusing = ConversationWorkQueue { throw IllegalStateException() }
        val reply = QueuedWorkConversationResponder(refusing).reply("do something")

        assertTrue(reply.contains("did not accept"))
        assertTrue(reply.contains("IllegalStateException"))
    }
}
