/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BridgeSessionStoreTest {

    @Test
    fun creation_and_existence() {
        val store = BridgeSessionStore()
        val session = store.create()
        assertNotNull(session.id)
        assertEquals(BridgeSession.UNTITLED, session.title)
        assertTrue(store.exists(session.id))
        
        val found = store.find(session.id)
        assertNotNull(found)
        assertEquals(session.id, found.id)
        assertEquals(0, found.turnCount)
    }

    @Test
    fun listing_ordered_by_recency() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val store = BridgeSessionStore(clock = { now })
        
        val s1 = store.create("S1")
        now = now.plusSeconds(10)
        val s2 = store.create("S2")
        
        // s2 is newer
        val list = store.list()
        assertEquals(2, list.size)
        assertEquals(s2.id, list[0].id)
        assertEquals(s1.id, list[1].id)

        // update s1
        now = now.plusSeconds(10)
        store.append(s1.id, TurnAuthor.OPERATOR, "updated s1")
        
        val listAfterUpdate = store.list()
        assertEquals(s1.id, listAfterUpdate[0].id)
        assertEquals(s2.id, listAfterUpdate[1].id)
    }

    @Test
    fun per_session_transcript_isolation() {
        val store = BridgeSessionStore()
        val s1 = store.create()
        val s2 = store.create()

        store.append(s1.id, TurnAuthor.OPERATOR, "hello s1")
        store.append(s2.id, TurnAuthor.OPERATOR, "hello s2")

        val t1 = store.transcript(s1.id)
        val t2 = store.transcript(s2.id)

        assertNotNull(t1)
        assertNotNull(t2)
        assertEquals(1, t1.size)
        assertEquals("hello s1", t1[0].text)
        assertEquals(1, t2.size)
        assertEquals("hello s2", t2[0].text)
    }

    @Test
    fun title_derived_from_first_operator_turn() {
        val store = BridgeSessionStore()
        val session = store.create()
        assertEquals(BridgeSession.UNTITLED, session.title)

        // First engine turn doesn't change title
        store.append(session.id, TurnAuthor.ENGINE, "system message")
        assertEquals(BridgeSession.UNTITLED, store.find(session.id)?.title)

        // First operator turn sets title
        store.append(session.id, TurnAuthor.OPERATOR, "create a new webapp tracking tasks")
        assertEquals("create a new webapp tracking tasks", store.find(session.id)?.title)

        // Subsequent operator turns do not overwrite title
        store.append(session.id, TurnAuthor.OPERATOR, "also add databases")
        assertEquals("create a new webapp tracking tasks", store.find(session.id)?.title)
    }

    @Test
    fun most_recent_selection() {
        var now = Instant.parse("2026-01-01T00:00:00Z")
        val store = BridgeSessionStore(clock = { now })
        assertNull(store.mostRecent())

        val s1 = store.create()
        assertEquals(s1.id, store.mostRecent()?.id)

        now = now.plusSeconds(5)
        val s2 = store.create()
        assertEquals(s2.id, store.mostRecent()?.id)

        now = now.plusSeconds(5)
        store.append(s1.id, TurnAuthor.OPERATOR, "wake up s1")
        assertEquals(s1.id, store.mostRecent()?.id)
    }

    @Test
    fun deletion_behavior() {
        val store = BridgeSessionStore()
        val s = store.create()
        assertTrue(store.exists(s.id))
        assertTrue(store.delete(s.id))
        assertFalse(store.exists(s.id))
        assertFalse(store.delete(s.id))
    }

    @Test
    fun session_count_eviction() {
        val store = BridgeSessionStore(maxSessions = 3)
        val s1 = store.create("S1")
        val s2 = store.create("S2")
        val s3 = store.create("S3")
        
        assertTrue(store.exists(s1.id))

        // Evicts s1
        val s4 = store.create("S4")
        assertFalse(store.exists(s1.id))
        assertTrue(store.exists(s2.id))
        assertTrue(store.exists(s3.id))
        assertTrue(store.exists(s4.id))
    }

    @Test
    fun turn_count_bounding() {
        val store = BridgeSessionStore(turnsPerSession = 5)
        val s = store.create()
        repeat(10) { store.append(s.id, TurnAuthor.OPERATOR, "turn-$it") }

        val transcript = store.transcript(s.id)
        assertNotNull(transcript)
        assertEquals(5, transcript.size)
        assertEquals("turn-5", transcript.first().text)
        assertEquals("turn-9", transcript.last().text)
    }

    @Test
    fun concurrent_append_safety() {
        val store = BridgeSessionStore(maxSessions = 10, turnsPerSession = 1000)
        val s = store.create()
        val threads = (1..8).map { w ->
            Thread {
                repeat(50) { store.append(s.id, TurnAuthor.OPERATOR, "w$w-$it") }
            }
        }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        val transcript = store.transcript(s.id)
        assertNotNull(transcript)
        assertEquals(400, transcript.size)
    }
}
