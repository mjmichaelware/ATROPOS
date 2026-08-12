/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.queue

import atropos.core.agent.AgentQueueRecord
import atropos.core.agent.AgentQueueRunResult
import atropos.core.agent.AgentQueueService

/**
 * Binds the bridge's queue seam to the real durable queue.
 *
 * The only place that knows both the client-facing view and the engine's
 * record shape. Keeping the translation here means the handler never sees an
 * [AgentQueueRecord] — with its lease tokens, meta file paths and recovery
 * counters — none of which a phone should receive.
 */
class AgentQueueWorkRunner(
    private val service: AgentQueueService = AgentQueueService(),
    private val activeProvider: () -> String
) : ConversationWorkRunner {

    override fun list(limit: Int): List<QueueEntryView> =
        service.list(limit).map(::view)

    override fun find(id: String): QueueEntryView? =
        service.resolve(id)?.let(::view)

    override fun run(id: String?): QueueRunOutcome {
        val provider = activeProvider()
        return runCatching {
            if (id.isNullOrBlank()) {
                translate(service.runNext(provider), requestedId = null)
            } else {
                if (service.resolve(id) == null) {
                    QueueRunOutcome.Unknown("No queue entry matches '$id'.")
                } else {
                    translate(service.resume(provider, id), requestedId = id)
                }
            }
        }.getOrElse { failure ->
            // A policy or agency refusal arrives as an exception and is the
            // operator's answer, not an internal error to swallow.
            QueueRunOutcome.Refused(
                failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName
            )
        }
    }

    override fun cancel(id: String, reason: String): QueueEntryView? =
        service.cancel(id, reason)?.let(::view)

    override fun throttled(): Boolean = runCatching { service.shouldThrottle() }.getOrDefault(false)

    private fun translate(result: AgentQueueRunResult, requestedId: String?): QueueRunOutcome {
        val entry = result.queueRecord?.let(::view)
        return when {
            result.ran -> QueueRunOutcome.Ran(entry, result.message)
            // Nothing eligible is a normal state for an idle queue, not a
            // failure: a client polling an empty queue must not see errors.
            requestedId == null && result.queueRecord == null ->
                QueueRunOutcome.NothingToRun(result.message)
            else -> QueueRunOutcome.Refused(result.message)
        }
    }

    private fun view(record: AgentQueueRecord) = QueueEntryView(
        id = record.id,
        task = record.task,
        state = record.state.name,
        checkpoint = record.checkpoint.name,
        attempts = record.attempts,
        maxAttempts = record.maxAttempts,
        terminal = record.state.terminal,
        failureReason = record.failureReason,
        evidence = record.sourceEvidence ?: record.contextExportPath,
        createdAt = record.createdAt.toString(),
        updatedAt = record.updatedAt.toString()
    )
}
