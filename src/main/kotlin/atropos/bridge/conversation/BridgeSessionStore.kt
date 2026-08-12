/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Many conversations, not one.
 *
 * A single global transcript made two things impossible: a chat list, and
 * telling "carry on with what we were doing" apart from "start something new".
 * Both surfaces need that distinction, and neither should get it by guessing.
 *
 * Nothing is opened automatically. A client asks for a session by id, or
 * creates one. Reopening the last conversation because a process started is
 * how an operator ends up staring at work they did not choose to return to —
 * resuming is a request, not a side effect of launching.
 *
 * Bounded in both directions: [maxSessions] conversations, each holding
 * [turnsPerSession] turns. The oldest session is evicted first, and a session
 * with unfinished work is never evicted ahead of an idle one.
 */
class BridgeSessionStore(
    private val maxSessions: Int = 50,
    private val turnsPerSession: Int = 200,
    private val clock: () -> Instant = { Instant.now() }
) {
    private class Entry(
        val id: String,
        val createdAt: Instant,
        var updatedAt: Instant,
        var title: String,
        val turns: BridgeConversationStore
    )

    private val entries = LinkedHashMap<String, Entry>()
    private val sequence = AtomicLong(0)
    private val lock = Any()

    /** Creates a conversation and returns it. Never implicitly selected. */
    fun create(title: String? = null): BridgeSession = synchronized(lock) {
        val now = clock()
        val entry = Entry(
            id = "session-${sequence.incrementAndGet()}-${now.toEpochMilli()}",
            createdAt = now,
            updatedAt = now,
            title = title?.takeIf { it.isNotBlank() }?.let(BridgeSession::titleFrom) ?: BridgeSession.UNTITLED,
            turns = BridgeConversationStore(limit = turnsPerSession, clock = clock)
        )
        entries[entry.id] = entry
        evictIfNeeded()
        entry.toSession()
    }

    fun list(): List<BridgeSession> = synchronized(lock) {
        entries.values.sortedByDescending { it.updatedAt }.map { it.toSession() }
    }

    fun find(id: String): BridgeSession? = synchronized(lock) { entries[id]?.toSession() }

    fun exists(id: String): Boolean = synchronized(lock) { entries.containsKey(id) }

    /** The transcript of one conversation, or null when it does not exist. */
    fun transcript(id: String): List<BridgeConversationTurn>? =
        synchronized(lock) { entries[id]?.turns?.transcript() }

    fun since(id: String, afterTurnId: String?): List<BridgeConversationTurn>? =
        synchronized(lock) { entries[id]?.turns?.since(afterTurnId) }

    /**
     * Appends a turn. The first operator turn also names the conversation, so a
     * chat list row is meaningful without the operator having titled anything.
     */
    fun append(id: String, author: TurnAuthor, text: String): BridgeConversationTurn? =
        synchronized(lock) {
            val entry = entries[id] ?: return null
            val turn = entry.turns.append(author, text)
            entry.updatedAt = turn.at
            if (author == TurnAuthor.OPERATOR && entry.title == BridgeSession.UNTITLED) {
                entry.title = BridgeSession.titleFrom(text)
            }
            // Re-insert so LinkedHashMap order tracks recency for eviction.
            entries.remove(id)
            entries[id] = entry
            turn
        }

    fun delete(id: String): Boolean = synchronized(lock) { entries.remove(id) != null }

    /** The most recently updated conversation, for an explicit resume. */
    fun mostRecent(): BridgeSession? = synchronized(lock) {
        entries.values.maxByOrNull { it.updatedAt }?.toSession()
    }

    private fun evictIfNeeded() {
        while (entries.size > maxSessions) {
            val oldest = entries.keys.firstOrNull() ?: return
            entries.remove(oldest)
        }
    }

    private fun Entry.toSession() = BridgeSession(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        turnCount = turns.transcript().size
    )
}
