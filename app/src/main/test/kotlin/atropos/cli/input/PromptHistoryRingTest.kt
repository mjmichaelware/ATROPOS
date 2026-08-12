/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptHistoryRingTest {

    @Test
    fun `lanes are recalled independently`() {
        val ring = PromptHistoryRing()
        ring.record(PromptHistoryLane.PROMPT, "prose line")
        ring.record(PromptHistoryLane.SLASH, "/status")

        assertEquals("prose line", ring.recall(PromptHistoryLane.PROMPT, 0))
        assertEquals("/status", ring.recall(PromptHistoryLane.SLASH, 0))
        assertTrue(ring.isEmpty(PromptHistoryLane.SHELL))
    }

    @Test
    fun `recall counts backwards from the newest entry`() {
        val ring = PromptHistoryRing()
        listOf("first", "second", "third").forEach { ring.record(PromptHistoryLane.PROMPT, it) }

        assertEquals("third", ring.recall(PromptHistoryLane.PROMPT, 0))
        assertEquals("second", ring.recall(PromptHistoryLane.PROMPT, 1))
        assertEquals("first", ring.recall(PromptHistoryLane.PROMPT, 2))
        assertNull(ring.recall(PromptHistoryLane.PROMPT, 3), "past the oldest entry is nothing")
    }

    @Test
    fun `consecutive duplicates are collapsed`() {
        val ring = PromptHistoryRing()
        repeat(4) { ring.record(PromptHistoryLane.PROMPT, "same") }
        assertEquals(1, ring.size(PromptHistoryLane.PROMPT))
    }

    @Test
    fun `a repeat that is not consecutive is kept`() {
        val ring = PromptHistoryRing()
        ring.record(PromptHistoryLane.PROMPT, "a")
        ring.record(PromptHistoryLane.PROMPT, "b")
        ring.record(PromptHistoryLane.PROMPT, "a")
        assertEquals(3, ring.size(PromptHistoryLane.PROMPT))
    }

    @Test
    fun `blank lines are never recorded`() {
        val ring = PromptHistoryRing()
        ring.record(PromptHistoryLane.PROMPT, "   ")
        ring.record(PromptHistoryLane.PROMPT, "")
        assertTrue(ring.isEmpty(PromptHistoryLane.PROMPT))
    }

    @Test
    fun `the oldest entry is evicted once the limit is reached`() {
        val ring = PromptHistoryRing(limit = 2)
        listOf("one", "two", "three").forEach { ring.record(PromptHistoryLane.PROMPT, it) }

        assertEquals(2, ring.size(PromptHistoryLane.PROMPT))
        assertEquals(listOf("two", "three"), ring.entries(PromptHistoryLane.PROMPT))
    }

    @Test
    fun `backwards search finds the newest match`() {
        val ring = PromptHistoryRing()
        listOf("build the thing", "run tests", "build again").forEach {
            ring.record(PromptHistoryLane.PROMPT, it)
        }
        assertEquals("build again", ring.searchBackwards(PromptHistoryLane.PROMPT, "build"))
    }

    @Test
    fun `backwards search is case-insensitive and misses cleanly`() {
        val ring = PromptHistoryRing()
        ring.record(PromptHistoryLane.PROMPT, "Deploy Now")
        assertEquals("Deploy Now", ring.searchBackwards(PromptHistoryLane.PROMPT, "deploy"))
        assertNull(ring.searchBackwards(PromptHistoryLane.PROMPT, "absent"))
    }

    @Test
    fun `an empty needle yields the newest entry`() {
        val ring = PromptHistoryRing()
        ring.record(PromptHistoryLane.PROMPT, "older")
        ring.record(PromptHistoryLane.PROMPT, "newest")
        assertEquals("newest", ring.searchBackwards(PromptHistoryLane.PROMPT, ""))
    }
}
