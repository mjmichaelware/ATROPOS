/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.bridge.queue.QueueEntryView
import atropos.bridge.queue.QueueRunOutcome
import atropos.bridge.queue.ConversationWorkRunner
import atropos.core.security.RedactionFilter

/**
 * Work a client can watch and advance.
 *
 * Without this, a phone could create queued work and then had no way to see or
 * run it: the operator had to move to the CLI, which defeats the point of the
 * surface. Listing is a read; running and cancelling change state and are
 * therefore POST.
 *
 * There are no path parameters because the route table matches exactly, by
 * design — an identifier arrives as `?id=`.
 */
internal class BridgeQueueHandler(
    private val runner: ConversationWorkRunner,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun list(request: HttpRequest): HttpResponse {
        val id = request.query["id"].orEmpty()
        if (id.isNotBlank()) {
            val entry = runner.find(id)
                ?: return HttpResponse.refusal(
                    404,
                    "queue-entry-unknown",
                    "No queue entry matches '$id'.",
                    "List the queue with GET /v1/queue to see current identifiers."
                )
            return HttpResponse.json(entryJson(entry))
        }

        val limit = request.query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val entries = runner.list(limit)
        return HttpResponse.json(
            JsonWriter.obj(
                "count" to JsonWriter.num(entries.size.toLong()),
                "throttled" to JsonWriter.bool(runner.throttled()),
                "entries" to JsonWriter.arr(entries.map(::entryJson))
            )
        )
    }

    /**
     * Runs the next entry, or a named one. Synchronous on purpose: the caller
     * asked for work to happen and the reply reports what happened, rather than
     * an acknowledgement the client would then have to poll to interpret.
     */
    fun run(request: HttpRequest): HttpResponse {
        val id = request.query["id"].orEmpty().ifBlank { null }
        return when (val outcome = runner.run(id)) {
            is QueueRunOutcome.Ran -> HttpResponse.json(
                JsonWriter.obj(
                    "ok" to JsonWriter.bool(true),
                    "ran" to JsonWriter.bool(true),
                    "message" to JsonWriter.str(redactionFilter.redact(outcome.message)),
                    "entry" to (outcome.entry?.let(::entryJson) ?: "null")
                )
            )
            is QueueRunOutcome.NothingToRun -> HttpResponse.json(
                JsonWriter.obj(
                    "ok" to JsonWriter.bool(true),
                    "ran" to JsonWriter.bool(false),
                    "message" to JsonWriter.str(redactionFilter.redact(outcome.message))
                )
            )
            is QueueRunOutcome.Unknown -> HttpResponse.refusal(
                404,
                "queue-entry-unknown",
                redactionFilter.compact(outcome.message),
                "List the queue with GET /v1/queue to see current identifiers."
            )
            is QueueRunOutcome.Refused -> HttpResponse.refusal(
                409,
                "queue-run-refused",
                redactionFilter.compact(outcome.message),
                "The engine declined to run this entry; its state explains why."
            )
        }
    }

    fun cancel(request: HttpRequest): HttpResponse {
        val id = request.query["id"].orEmpty()
        if (id.isBlank()) {
            return HttpResponse.badRequest(
                "Cancelling needs an 'id'.",
                "POST /v1/queue/cancel?id=<queue-id>"
            )
        }
        val reason = request.query["reason"].orEmpty().ifBlank { "cancelled from a client surface" }
        val entry = runner.cancel(id, reason)
            ?: return HttpResponse.refusal(
                404,
                "queue-entry-unknown",
                "No queue entry matches '$id'.",
                "List the queue with GET /v1/queue to see current identifiers."
            )
        return HttpResponse.json(
            JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "entry" to entryJson(entry)
            )
        )
    }

    private fun entryJson(entry: QueueEntryView): String = JsonWriter.obj(
        "id" to JsonWriter.str(entry.id),
        "task" to JsonWriter.str(redactionFilter.redact(entry.task)),
        "state" to JsonWriter.str(entry.state),
        "checkpoint" to JsonWriter.str(entry.checkpoint),
        "attempts" to JsonWriter.num(entry.attempts.toLong()),
        "maxAttempts" to JsonWriter.num(entry.maxAttempts.toLong()),
        "terminal" to JsonWriter.bool(entry.terminal),
        "failureReason" to JsonWriter.str(redactionFilter.redact(entry.failureReason.orEmpty())),
        "evidence" to JsonWriter.str(redactionFilter.redact(entry.evidence.orEmpty())),
        "createdAt" to JsonWriter.str(entry.createdAt),
        "updatedAt" to JsonWriter.str(entry.updatedAt)
    )
}
