/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.checkpoint.CheckpointAction
import atropos.core.checkpoint.CheckpointSummary
import java.time.Instant
import atropos.core.security.RedactionFilter

/**
 * Projects the resume checkpoint onto the wire.
 *
 * The primary action is emitted as engine-computed data rather than left to the
 * surface, because `HOE-C04`/`HOE-B04` make Resume the primary and explicitly
 * not "new chat" — a rule the Web cannot be trusted to re-derive, since the
 * cheapest layout a renderer reaches for is two equal-weight buttons.
 *
 * The full action set is emitted too, so a surface can render the secondary
 * without inventing one. There is deliberately no "start over" action here: the
 * engine does not offer to discard resumable state, so the surface has nothing
 * to bind such a control to.
 */
class CheckpointProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun render(summary: CheckpointSummary?, now: Instant): String {
        if (summary == null) {
            // Absence is stated, not rendered as a fresh checkpoint at zero.
            return JsonWriter.obj(
                "ok" to JsonWriter.bool(true),
                "present" to JsonWriter.bool(false),
                "detail" to JsonWriter.str("No checkpoint has been recorded for this workspace."),
                "remedy" to JsonWriter.str("Run a goal; a checkpoint is written as it advances.")
            )
        }

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "present" to JsonWriter.bool(true),
            "goalId" to JsonWriter.str(redactionFilter.redact(summary.goalId)),
            "nodeId" to JsonWriter.nullable(summary.nodeId?.let(redactionFilter::redact)),
            "phase" to JsonWriter.nullable(summary.phase?.let(redactionFilter::redact)),
            "recordedAt" to JsonWriter.str(summary.recordedAt.toString()),
            "ageMinutes" to JsonWriter.num(summary.ageAt(now).toMinutes()),
            "resumable" to JsonWriter.bool(summary.resumable),
            "evidenceCount" to JsonWriter.num(summary.evidenceCount),
            "nextAction" to JsonWriter.nullable(summary.nextAction?.let(redactionFilter::redact)),
            "primaryAction" to JsonWriter.obj(
                "id" to JsonWriter.str(redactionFilter.redact(summary.primaryAction.canonical)),
                "label" to JsonWriter.str(redactionFilter.redact(summary.primaryAction.label))
            ),
            "actions" to JsonWriter.arr(
                CheckpointAction.entries.map {
                    JsonWriter.obj(
                        "id" to JsonWriter.str(redactionFilter.redact(it.canonical)),
                        "label" to JsonWriter.str(redactionFilter.redact(it.label)),
                        "primary" to JsonWriter.bool(it == summary.primaryAction)
                    )
                }
            )
        )
    }
}
