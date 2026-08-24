/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.bridge.queue.ConversationWorkRunner
import atropos.core.approval.PendingApprovalStore
import atropos.bridge.http.StreamSink
import atropos.core.security.RedactionFilter

internal class BridgeEventsHandler(
    private val work: ConversationWorkRunner?,
    private val approvals: PendingApprovalStore,
    private val sessions: BridgeSessionStore,
    private val defaultStore: BridgeConversationStore,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun getEvents(request: HttpRequest): HttpResponse {
        val cursor = request.query["after"]?.toLongOrNull() ?: 0L
        val sessionId = request.query["session"].orEmpty().trim()
        BridgeEventHub.detectChanges(work, approvals, sessions, defaultStore)

        val newEvents = filter(BridgeEventHub.getAfter(cursor), sessionId)
        val eventListJson = newEvents.map { event ->
            JsonWriter.obj(
                "cursor" to JsonWriter.num(event.cursor),
                "type" to JsonWriter.str(event.type),
                "timestamp" to JsonWriter.str(event.timestamp.toString()),
                "detail" to JsonWriter.str(redactionFilter.redact(event.detail))
            )
        }

        return HttpResponse.json(
            JsonWriter.obj(
                "count" to JsonWriter.num(newEvents.size.toLong()),
                "events" to JsonWriter.arr(eventListJson)
            )
        )
    }

    fun streamEvents(
        request: HttpRequest,
        sink: StreamSink,
        intervalMillis: Long,
        maxFrames: Int,
        sleep: (Long) -> Unit
    ) {
        var cursor = request.query["after"]?.toLongOrNull() ?: 0L
        val sessionId = request.query["session"].orEmpty().trim()
        var frames = 0
        while (sink.isOpen() && frames < maxFrames) {
            BridgeEventHub.detectChanges(work, approvals, sessions, defaultStore)
            filter(BridgeEventHub.getAfter(cursor), sessionId).forEach { event ->
                if (sink.emit("event", JsonWriter.obj(
                        "cursor" to JsonWriter.num(event.cursor),
                        "type" to JsonWriter.str(event.type),
                        "timestamp" to JsonWriter.str(event.timestamp.toString()),
                        "detail" to JsonWriter.str(redactionFilter.redact(event.detail))
                    ))) cursor = maxOf(cursor, event.cursor)
            }
            frames += 1
            if (frames < maxFrames) sleep(intervalMillis)
        }
    }

    private fun filter(events: List<BridgeEventHub.Event>, sessionId: String): List<BridgeEventHub.Event> =
        if (sessionId.isBlank()) events else events.filter { event ->
            event.detail.split(' ', '\n').any { it == "session=$sessionId" }
        }
}
