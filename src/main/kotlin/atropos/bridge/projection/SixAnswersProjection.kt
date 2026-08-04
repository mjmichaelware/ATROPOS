/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.cli.ui.DashboardRenderer
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.HoeStatusVocabulary
import atropos.core.security.RedactionFilter

/**
 * Projects the six continuous answers onto the wire.
 *
 * Source Doc 4 §0.1 requires the same six answers on every primary surface, and
 * `HOE-C02` requires the Web surface to be a thin presentation of them — "no
 * business logic in Web". That only holds if the answers cross the boundary as
 * *answers*, already computed. [DashboardRenderer.DashboardState] is the CLI's
 * computed form, so the Web surface is fed from the same value the terminal
 * draws; nothing here recomputes an answer, and a Web client that disagreed
 * with the terminal would be a bug in this file rather than a difference of
 * opinion between two implementations.
 *
 * Two invariants are carried across deliberately:
 *
 * `readable` is emitted separately from emptiness. §4.1 forbids collapsing "I
 * cannot read the queue" into "there is no work", and a JSON array cannot
 * express that difference on its own.
 *
 * Every string is redacted here rather than trusted from upstream. The provider
 * already redacts, so this is a second pass on the same text — which is the
 * point: `HOE-A10` makes redaction a property of the render boundary, so a
 * future caller that assembles a state by hand cannot bypass it.
 */
class SixAnswersProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun render(state: DashboardRenderer.DashboardState): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "answers" to answers(state.answers),
        "queue" to JsonWriter.obj(
            "readable" to JsonWriter.bool(state.queueReadable),
            "queued" to JsonWriter.num(state.queuedItems),
            "failed" to JsonWriter.num(state.failedItems),
            "running" to JsonWriter.arr(state.runningWork.map(::workItem))
        ),
        "projectsReadable" to JsonWriter.bool(state.projectsReadable),
        "provider" to JsonWriter.str(redact(state.provider)),
        "heap" to JsonWriter.obj(
            "usedMb" to JsonWriter.num(state.heapUsedMb),
            "maxMb" to JsonWriter.num(state.heapMaxMb)
        )
    )

    private fun answers(six: DashboardRenderer.SixAnswers): String = JsonWriter.obj(
        "objective" to answer(six.objective),
        "doing" to answer(six.doing),
        "why" to answer(six.why),
        "progress" to answer(six.progress),
        "next" to answer(six.next),
        "evidence" to answer(six.evidence)
    )

    /**
     * One answer, with its health carried in three redundant channels.
     *
     * `health` is the machine term, `signal` the non-colour word, and `value`
     * the sentence. Source Doc 3 §E requires colour to be paired with a
     * non-colour channel, and a surface can only honour that if the non-colour
     * channel actually crosses the wire.
     */
    private fun answer(value: DashboardRenderer.Answer): String = JsonWriter.obj(
        "value" to JsonWriter.str(redact(value.value)),
        "health" to JsonWriter.str(value.health.name.lowercase()),
        "signal" to JsonWriter.str(signal(value.health))
    )

    private fun workItem(item: DashboardRenderer.WorkItem): String = JsonWriter.obj(
        "id" to JsonWriter.str(redact(item.id)),
        "title" to JsonWriter.str(redact(item.title)),
        "state" to JsonWriter.str(HoeStatusVocabulary.termFor(item.state) ?: item.state.name.lowercase()),
        "detail" to JsonWriter.str(redact(item.detail)),
        "attempt" to (item.attempt?.let(JsonWriter::num) ?: "null"),
        "maxAttempts" to (item.maxAttempts?.let(JsonWriter::num) ?: "null")
    )

    private fun signal(health: Health): String = when (health) {
        Health.VERIFIED -> "verified"
        Health.PENDING -> "pending"
        Health.ERROR -> "error"
        Health.UNKNOWN -> "unknown"
    }

    private fun redact(value: String): String = redactionFilter.redact(value)
}
