/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.recovery.StateSnapshot
import atropos.core.security.RedactionFilter

/** Renders the existing restart snapshot for recovery ribbons and diagnostics. */
class RecoveryProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun render(snapshot: StateSnapshot?): String {
        if (snapshot == null) {
            return JsonWriter.obj(
                "available" to JsonWriter.bool(false),
                "reason" to JsonWriter.str("recovery-snapshot-not-wired")
            )
        }
        val report = snapshot.recoveryReport
        return JsonWriter.obj(
            "available" to JsonWriter.bool(true),
            "id" to JsonWriter.str(snapshot.id),
            "capturedAt" to JsonWriter.str(snapshot.capturedAt.toString()),
            "restored" to JsonWriter.num((report?.interruptedRuns ?: 0).toLong()),
            "rebuilt" to JsonWriter.num((report?.completedMutationsSkipped ?: 0).toLong()),
            "failed" to JsonWriter.num((report?.errors?.size ?: 0).toLong()),
            "goalRuns" to JsonWriter.num(snapshot.goalRuns.size.toLong()),
            "dags" to JsonWriter.num(snapshot.dags.size.toLong()),
            "message" to JsonWriter.str(redactionFilter.redact(report?.message.orEmpty())),
            "errors" to JsonWriter.arr(report?.errors.orEmpty().map(redactionFilter::redact).map(JsonWriter::str))
        )
    }
}
