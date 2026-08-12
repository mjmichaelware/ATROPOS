/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The transcript a bridge client reads and appends to.
 *
 * Bounded on purpose. A phone polling a long-lived engine would otherwise grow
 * this without limit, and the oldest turns are the ones least likely to be
 * rendered. [limit] is the number of turns retained; older ones are dropped
 * from the front.
 *
 * In memory rather than durable: this is the live conversation surface, and
 * the durable record of what the engine did is the run journal and evidence
 * store, which already own that responsibility. Persisting turns here would
 * create a second history that could disagree with them.
 */
class BridgeConversationStore(
    private val limit: Int = 200,
    private val clock: () -> Instant = { Instant.now() }
) {
    private val turns = ArrayDeque<BridgeConversationTurn>()
    private val sequence = AtomicLong(0)
    private val lock = Any()

    fun append(author: TurnAuthor, text: String): BridgeConversationTurn {
        val turn = BridgeConversationTurn(
            id = "turn-${sequence.incrementAndGet()}",
            author = author,
            text = text,
            at = clock()
        )
        synchronized(lock) {
            turns.addLast(turn)
            while (turns.size > limit) turns.removeFirst()
        }
        return turn
    }

    /** The transcript, oldest first. */
    fun transcript(): List<BridgeConversationTurn> = synchronized(lock) { turns.toList() }

    /** Turns recorded after [afterId], for a client that already has a prefix. */
    fun since(afterId: String?): List<BridgeConversationTurn> {
        if (afterId.isNullOrBlank()) return transcript()
        val all = transcript()
        val index = all.indexOfFirst { it.id == afterId }
        return if (index < 0) all else all.drop(index + 1)
    }

    fun clear() {
        synchronized(lock) { turns.clear() }
    }
}
