/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposerOutboxTest {

    @Test
    fun a_message_typed_while_offline_is_kept() {
        // The composer has always promised this. Until now nothing kept it:
        // the field was cleared before the send outcome was known.
        val outbox = ComposerOutbox().queue("build the thing")
        assertEquals("build the thing", outbox.head())
        assertEquals(1, outbox.size)
    }

    @Test
    fun messages_replay_in_the_order_they_were_typed() {
        // Out of order replay produces a conversation the operator did not have.
        val outbox = ComposerOutbox().queue("first").queue("second").queue("third")
        assertEquals(listOf("first", "second", "third"), outbox.pending)
        assertEquals("second", outbox.dropHead().head())
    }

    @Test
    fun the_head_is_dropped_only_by_an_explicit_call() {
        // Draining on *attempt* rather than on confirmed delivery would lose
        // the message on exactly the failure the queue exists to survive.
        val outbox = ComposerOutbox().queue("only")
        assertEquals("only", outbox.head())
        assertEquals("only", outbox.head(), "reading the head must not consume it")
        assertTrue(outbox.dropHead().isEmpty)
    }

    @Test
    fun blank_input_is_not_queued() {
        assertTrue(ComposerOutbox().queue("   ").isEmpty)
        assertTrue(ComposerOutbox().queue("").isEmpty)
    }

    @Test
    fun surrounding_whitespace_is_trimmed_but_the_message_is_kept_intact() {
        assertEquals("hello", ComposerOutbox().queue("  hello  ").head())
    }

    @Test
    fun an_identical_message_typed_twice_is_kept_twice() {
        // Two identical messages are two things the operator said; collapsing
        // them would make the transcript disagree with what they typed.
        val outbox = ComposerOutbox().queue("again").queue("again")
        assertEquals(2, outbox.size)
    }

    @Test
    fun dropping_from_an_empty_queue_is_harmless() {
        assertTrue(ComposerOutbox().dropHead().isEmpty)
        assertNull(ComposerOutbox().head())
    }

    @Test
    fun an_empty_queue_says_nothing_at_all() {
        // A counter that is usually zero is noise, and noise teaches people to
        // stop reading the line that eventually matters.
        assertNull(ComposerOutbox().describe())
    }

    @Test
    fun the_notice_counts_correctly_and_does_not_overpromise() {
        val one = ComposerOutbox().queue("a").describe().orEmpty()
        assertTrue(one.startsWith("1 message queued"))
        // "this session" is stated because the queue is in memory. Claiming
        // durability the code does not provide is the mistake being corrected.
        assertTrue(one.contains("this session"))

        val two = ComposerOutbox().queue("a").queue("b").describe().orEmpty()
        assertTrue(two.startsWith("2 messages queued"))
    }

    @Test
    fun clearing_empties_the_queue() {
        assertTrue(ComposerOutbox().queue("a").queue("b").cleared().isEmpty)
    }
}
