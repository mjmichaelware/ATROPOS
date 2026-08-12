/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeConversationStoreTest {

    @Test
    fun transcript_preserves_order_and_authorship() {
        val store = BridgeConversationStore()
        store.append(TurnAuthor.OPERATOR, "build me a tracker")
        store.append(TurnAuthor.ENGINE, "received")

        val turns = store.transcript()
        assertEquals(2, turns.size)
        assertEquals(TurnAuthor.OPERATOR, turns[0].author)
        assertEquals(TurnAuthor.ENGINE, turns[1].author)
        assertEquals("build me a tracker", turns[0].text)
    }

    @Test
    fun turn_ids_are_unique_so_a_client_can_key_a_list_on_them() {
        val store = BridgeConversationStore()
        repeat(50) { store.append(TurnAuthor.OPERATOR, "m$it") }
        val ids = store.transcript().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "turn ids must be unique")
    }

    /**
     * A phone polling a long-lived engine would otherwise grow the transcript
     * without bound.
     */
    @Test
    fun transcript_is_bounded_and_drops_the_oldest_turns() {
        val store = BridgeConversationStore(limit = 10)
        repeat(25) { store.append(TurnAuthor.OPERATOR, "m$it") }

        val turns = store.transcript()
        assertEquals(10, turns.size)
        assertEquals("m15", turns.first().text, "oldest turns are dropped, not newest")
        assertEquals("m24", turns.last().text)
    }

    @Test
    fun since_returns_only_turns_after_the_supplied_id() {
        val store = BridgeConversationStore()
        store.append(TurnAuthor.OPERATOR, "first")
        val second = store.append(TurnAuthor.ENGINE, "second")
        store.append(TurnAuthor.OPERATOR, "third")

        val delta = store.since(second.id)
        assertEquals(1, delta.size)
        assertEquals("third", delta.single().text)
    }

    /**
     * A client holding an id the store has already evicted must not silently
     * receive nothing; it needs the whole transcript so its view can resync.
     */
    @Test
    fun since_falls_back_to_the_full_transcript_for_an_unknown_id() {
        val store = BridgeConversationStore()
        store.append(TurnAuthor.OPERATOR, "only")

        assertEquals(1, store.since("turn-does-not-exist").size)
    }

    @Test
    fun since_returns_everything_when_no_cursor_is_supplied() {
        val store = BridgeConversationStore()
        store.append(TurnAuthor.OPERATOR, "a")
        store.append(TurnAuthor.ENGINE, "b")

        assertEquals(2, store.since(null).size)
        assertEquals(2, store.since("").size)
    }

    @Test
    fun turns_carry_the_supplied_clock_instant() {
        val fixed = Instant.parse("2026-01-01T00:00:00Z")
        val store = BridgeConversationStore(clock = { fixed })

        assertEquals(fixed, store.append(TurnAuthor.OPERATOR, "x").at)
    }

    @Test
    fun concurrent_appends_do_not_lose_turns() {
        val store = BridgeConversationStore(limit = 1_000)
        val threads = (1..8).map { worker ->
            Thread { repeat(50) { store.append(TurnAuthor.OPERATOR, "w$worker-$it") } }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertEquals(400, store.transcript().size)
        assertTrue(store.transcript().map { it.id }.toSet().size == 400, "ids stay unique under contention")
    }
}
