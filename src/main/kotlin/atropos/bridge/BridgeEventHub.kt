/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.queue.ConversationWorkRunner
import atropos.core.approval.PendingApprovalStore
import java.time.Instant

object BridgeEventHub {
    data class Event(val cursor: Long, val type: String, val timestamp: Instant, val detail: String)
    
    private val events = ArrayList<Event>()
    private val sequence = java.util.concurrent.atomic.AtomicLong(0)
    private const val MAX_EVENTS = 500
    private val lock = Any()

    private val lastQueueStates = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val lastApprovalIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val lastTurnCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var lastDefaultStoreTurnCount = 0

    fun emit(type: String, detail: String) = synchronized(lock) {
        val cursor = sequence.incrementAndGet()
        events.add(Event(cursor, type, Instant.now(), detail))
        while (events.size > MAX_EVENTS) {
            events.removeAt(0)
        }
    }

    fun getAfter(cursor: Long): List<Event> = synchronized(lock) {
        events.filter { it.cursor > cursor }
    }

    fun detectChanges(
        work: ConversationWorkRunner?,
        approvals: PendingApprovalStore,
        sessions: BridgeSessionStore,
        defaultStore: BridgeConversationStore
    ) = synchronized(lock) {
        if (work != null) {
            val currentQueue = work.list(100)
            for (entry in currentQueue) {
                val previousState = lastQueueStates[entry.id]
                if (previousState != null && previousState != entry.state) {
                    emit("queue_state_changed", "id=${entry.id} previous=$previousState current=${entry.state}")
                }
                lastQueueStates[entry.id] = entry.state
            }
        }

        val currentApprovals = approvals.pending()
        for (app in currentApprovals) {
            if (!lastApprovalIds.contains(app.id)) {
                emit("approval_raised", "id=${app.id} actor=${app.actor} op=${app.operation}")
                lastApprovalIds.add(app.id)
            }
        }
        val activeIds = currentApprovals.map { it.id }.toSet()
        lastApprovalIds.removeIf { it !in activeIds }

        val activeSessions = sessions.list()
        for (session in activeSessions) {
            val previousCount = lastTurnCounts[session.id]
            if (previousCount != null && session.turnCount > previousCount) {
                emit("turn_appended", "session=${session.id} count=${session.turnCount}")
            }
            lastTurnCounts[session.id] = session.turnCount
        }

        val defaultCount = defaultStore.transcript().size
        if (lastDefaultStoreTurnCount > 0 && defaultCount > lastDefaultStoreTurnCount) {
            emit("turn_appended", "session=default count=$defaultCount")
        }
        lastDefaultStoreTurnCount = defaultCount
    }

    fun clear() = synchronized(lock) {
        events.clear()
        sequence.set(0)
        lastQueueStates.clear()
        lastApprovalIds.clear()
        lastTurnCounts.clear()
        lastDefaultStoreTurnCount = 0
    }
}
