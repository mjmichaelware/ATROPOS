/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.integration.PipelineField
import atropos.core.monitor.ActivityStage
import atropos.core.monitor.ActivityStream

/**
 * Projects the activity monitor's single stream onto the wire.
 *
 * `C3-P19` is a presentation atom — "no second event system" — so this reads
 * [ActivityStream] and adds nothing. In particular it does not compute a health
 * verdict: `isComplete` means every stage reported, which is a coverage fact,
 * and a surface that read it as "the run succeeded" would report a fully-failed
 * pipeline as finished.
 *
 * Missing stages are emitted by name so the monitor can render a gap as a gap
 * rather than as a shorter list.
 */
class ActivityProjection {

    fun render(stream: ActivityStream): String {
        val ordered = stream.ordered()
        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "stages" to JsonWriter.strArr(ActivityStage.entries.map { it.canonical }),
            "pipeline" to JsonWriter.arr(pipelineFields().map { field ->
                JsonWriter.obj(
                    "stage" to JsonWriter.str(field.stage),
                    "description" to JsonWriter.str(field.description),
                    "how" to JsonWriter.str(field.howDescription)
                )
            }),
            "missingStages" to JsonWriter.strArr(stream.missingStages().map { it.canonical }),
            // Coverage, not health. Named so the surface cannot mistake it.
            "everyStageReported" to JsonWriter.bool(stream.isComplete()),
            "events" to JsonWriter.arr(
                ordered.map { event ->
                    JsonWriter.obj(
                        "id" to JsonWriter.str(event.id),
                        "at" to JsonWriter.str(event.at.toString()),
                        "stage" to JsonWriter.str(event.stage.canonical),
                        "subject" to JsonWriter.str(event.subject),
                        "outcome" to JsonWriter.str(event.outcome),
                        "detail" to JsonWriter.str(event.detail)
                    )
                }
            )
        )
    }

    private fun pipelineFields(): List<PipelineField> = ActivityStage.entries.map { stage ->
        PipelineField(
            stage = stage.canonical,
            description = "${stage.canonical} state from the canonical activity stream",
            howDescription = "Read the ${stage.canonical} events already present in ActivityStream"
        )
    }
}
