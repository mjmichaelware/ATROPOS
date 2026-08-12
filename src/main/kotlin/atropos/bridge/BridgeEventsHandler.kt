/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.bridge.queue.ConversationWorkRunner
import atropos.core.approval.PendingApprovalStore

internal class BridgeEventsHandler(
    private val work: ConversationWorkRunner?,
    private val approvals: PendingApprovalStore,
    private val sessions: BridgeSessionStore,
    private val defaultStore: BridgeConversationStore
) {
    fun getEvents(request: HttpRequest): HttpResponse {
        val cursor = request.query["after"]?.toLongOrNull() ?: 0L
        BridgeEventHub.detectChanges(work, approvals, sessions, defaultStore)

        val newEvents = BridgeEventHub.getAfter(cursor)
        val eventListJson = newEvents.map { event ->
            JsonWriter.obj(
                "cursor" to JsonWriter.num(event.cursor),
                "type" to JsonWriter.str(event.type),
                "timestamp" to JsonWriter.str(event.timestamp.toString()),
                "detail" to JsonWriter.str(event.detail)
            )
        }

        return HttpResponse.json(
            JsonWriter.obj(
                "count" to JsonWriter.num(newEvents.size.toLong()),
                "events" to JsonWriter.arr(eventListJson)
            )
        )
    }
}
