/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.cli.ui.DashboardRenderer
import atropos.cli.ui.design.HoeStatusVocabulary
import atropos.core.security.RedactionFilter

/**
 * Projects the durable project registry onto the wire.
 *
 * `HOE-A03` makes the project the durable organisational boundary and requires
 * it to survive restart. The registry on disk already is that boundary, so this
 * file adds no identity of its own — it reads the same [DashboardRenderer.ProjectSummary]
 * the cockpit renders and serialises it. A Web surface that minted its own
 * project ids would break `HOE-F01`, which requires the same project identity
 * on CLI, Web and Android.
 *
 * `readable` is separate from the list for the same reason it is on the queue:
 * an unreadable registry is a fault, an empty one is a nominal new workspace,
 * and an empty array cannot say which happened.
 */
class ProjectProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun render(state: DashboardRenderer.DashboardState): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "readable" to JsonWriter.bool(state.projectsReadable),
        "projects" to JsonWriter.arr(state.projects.map(::project))
    )

    private fun project(summary: DashboardRenderer.ProjectSummary): String = JsonWriter.obj(
        "id" to JsonWriter.str(redactionFilter.redact(summary.id)),
        "name" to JsonWriter.str(redactionFilter.redact(summary.name)),
        "status" to JsonWriter.str(
            HoeStatusVocabulary.termFor(summary.status) ?: summary.status.name.lowercase()
        ),
        "statusLabel" to JsonWriter.str(summary.statusLabel),
        "signal" to JsonWriter.str(HoeStatusVocabulary.signal(summary.status).text),
        "objective" to JsonWriter.str(redactionFilter.redact(summary.objective)),
        // §3.4: a project claiming completion it cannot prove must be visibly
        // distinguishable from one that can, so the flag crosses the wire.
        "completionIsVerifiable" to JsonWriter.bool(summary.completionIsVerifiable)
    )
}
