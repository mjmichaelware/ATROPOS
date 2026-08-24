/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeSession
import atropos.bridge.conversation.BridgeSessionStore
import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.security.RedactionFilter

/**
 * The chat list, and the explicit resume.
 *
 * `GET /v1/sessions` is what a client draws a conversation list from.
 * `POST /v1/sessions` starts a new one. `GET /v1/sessions/recent` answers
 * "what was I last doing" *without* selecting it — a client shows it as an
 * offer, and the operator chooses. Nothing here reopens a conversation on its
 * own, which is the whole point: returning to previous work is a request, not
 * something that happens because a surface loaded.
 */
internal class BridgeSessionHandler(
    private val sessions: BridgeSessionStore,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun list(request: HttpRequest): HttpResponse {
        val id = request.query["id"].orEmpty()
        if (id.isNotBlank()) {
            val session = sessions.find(id) ?: return unknown(id)
            return HttpResponse.json(sessionJson(session))
        }
        val all = sessions.list()
        return HttpResponse.json(
            JsonWriter.obj(
                "count" to JsonWriter.num(all.size.toLong()),
                "sessions" to JsonWriter.arr(all.map(::sessionJson))
            )
        )
    }

    fun create(request: HttpRequest): HttpResponse {
        val title = request.query["title"].orEmpty()
        val session = sessions.create(title.takeIf { it.isNotBlank() })
        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "session" to sessionJson(session)
            )
        )
    }

    /**
     * The last conversation, offered rather than opened.
     *
     * Answering with `resumable:false` and a null session is the truthful reply
     * for a fresh runtime, and a client must render that as "start something
     * new" instead of an empty conversation that looks like a lost one.
     */
    fun recent(): HttpResponse {
        val session = sessions.mostRecent()
        return HttpResponse.json(
            JsonWriter.obj(
                "resumable" to JsonWriter.bool(session != null),
                "session" to (session?.let(::sessionJson) ?: "null")
            )
        )
    }

    fun delete(request: HttpRequest): HttpResponse {
        val id = request.query["id"].orEmpty()
        if (id.isBlank()) {
            return HttpResponse.badRequest(
                "Deleting a conversation needs an 'id'.",
                "POST /v1/sessions/delete?id=<session-id>"
            )
        }
        if (!sessions.delete(id)) return unknown(id)
        return HttpResponse.json(
            JsonWriter.obj("ok" to JsonWriter.bool(true), "deleted" to JsonWriter.str(id))
        )
    }

    private fun unknown(id: String) = HttpResponse.refusal(
        404,
        "session-unknown",
        "No conversation matches '$id'.",
        "List conversations with GET /v1/sessions."
    )

    private fun sessionJson(session: BridgeSession): String = JsonWriter.obj(
        "id" to JsonWriter.str(session.id),
        "title" to JsonWriter.str(redactionFilter.redact(session.title)),
        "turnCount" to JsonWriter.num(session.turnCount.toLong()),
        "createdAt" to JsonWriter.str(session.createdAt.toString()),
        "updatedAt" to JsonWriter.str(session.updatedAt.toString())
    )
}
