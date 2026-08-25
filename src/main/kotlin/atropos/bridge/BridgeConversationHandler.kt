/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.conversation.BridgeConversationResponder
import atropos.bridge.conversation.BridgeConversationStore
import atropos.bridge.conversation.BridgeConversationTurn
import atropos.bridge.conversation.TurnAuthor
import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.security.RedactionFilter

/**
 * HOE-D02: the conversation half of the local engine bridge.
 *
 * Two routes, because a phone client needs exactly two things: append a turn,
 * and read the transcript. Everything else a conversation surface might want —
 * status, approvals, evidence — is already its own route and is not duplicated
 * here.
 *
 * Both directions are redacted. A message typed on a phone can contain a
 * credential, and a reply can quote one back; this is a rendered surface like
 * any other, so the no-raw-secret rule applies in full.
 */
internal class BridgeConversationHandler(
    private val store: BridgeConversationStore,
    private val responder: BridgeConversationResponder,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    /**
     * Conversations, when this build has them. A request naming `?session=`
     * is routed to that conversation; one that does not keeps using the single
     * store, so a client written before sessions existed still works.
     */
    private val sessions: atropos.bridge.conversation.BridgeSessionStore? = null
) {
    fun postMessage(request: HttpRequest): HttpResponse {
        val text = request.query["text"].orEmpty().ifBlank { field(request.body, "text") }.trim()
        if (text.isBlank()) {
            return HttpResponse.badRequest(
                "A message needs non-empty 'text'.",
                "POST /v1/message with {\"text\": \"...\"}"
            )
        }
        if (text.length > MAX_MESSAGE_CHARS) {
            return HttpResponse.badRequest(
                "Message is longer than $MAX_MESSAGE_CHARS characters.",
                "Send a shorter message; the bridge bounds turn size."
            )
        }

        val session = request.query["session"].orEmpty()
        if (session.isNotBlank() && sessions?.exists(session) == false) {
            return HttpResponse.refusal(
                404,
                "session-unknown",
                "No conversation matches '$session'.",
                "List conversations with GET /v1/sessions, or start one with POST /v1/sessions."
            )
        }

        val operatorTurn = append(session, TurnAuthor.OPERATOR, redactionFilter.redact(text))

        // A responder that throws would lose the operator's turn with no reply
        // and no error, so failure is recorded as a turn like any other.
        val replyText = runCatching { responder.reply(text) }
            .getOrElse { failure -> "The engine could not answer (${failure.javaClass.simpleName})." }
        val engineTurn = append(session, TurnAuthor.ENGINE, redactionFilter.redact(replyText))

        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "accepted" to turnJson(operatorTurn),
                "reply" to turnJson(engineTurn)
            )
        )
    }

    fun getMessages(request: HttpRequest): HttpResponse {
        val session = request.query["session"].orEmpty()
        val turns = if (session.isNotBlank() && sessions != null) {
            sessions.since(session, request.query["after"])
                ?: return HttpResponse.refusal(
                    404,
                    "session-unknown",
                    "No conversation matches '$session'.",
                    "List conversations with GET /v1/sessions."
                )
        } else {
            store.since(request.query["after"])
        }
        return HttpResponse.json(
            JsonWriter.obj(
                "count" to JsonWriter.num(turns.size.toLong()),
                "turns" to JsonWriter.arr(turns.map(::turnJson))
            )
        )
    }

    /** Appends to the named conversation, or the default store when unnamed. */
    private fun append(session: String, author: TurnAuthor, text: String): BridgeConversationTurn =
        if (session.isNotBlank() && sessions != null) {
            sessions.append(session, author, text) ?: store.append(author, text)
        } else {
            store.append(author, text)
        }

    private fun turnJson(turn: BridgeConversationTurn): String = JsonWriter.obj(
        "id" to JsonWriter.str(turn.id),
        "author" to JsonWriter.str(turn.author.name.lowercase()),
        "text" to JsonWriter.str(redactionFilter.redact(turn.text)),
        "at" to JsonWriter.str(turn.at.toString())
    )

    /** Minimal field read for a flat JSON body, matching the approval handler. */
    private fun field(body: String, name: String): String {
        val marker = "\"$name\""
        val at = body.indexOf(marker)
        if (at < 0) return ""
        val colon = body.indexOf(':', at + marker.length)
        if (colon < 0) return ""
        var i = colon + 1
        while (i < body.length && body[i].isWhitespace()) i++
        if (i >= body.length || body[i] != '"') return ""
        i++
        val out = StringBuilder()
        while (i < body.length && body[i] != '"') {
            if (body[i] == '\\' && i + 1 < body.length) {
                when (val escaped = body[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(escaped)
                }
                i += 2
            } else {
                out.append(body[i]); i++
            }
        }
        return out.toString()
    }

    private companion object {
        const val MAX_MESSAGE_CHARS = 8_000
    }
}
