/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.checkpoint

import java.time.Duration
import java.time.Instant

/**
 * A checkpoint as the operator sees it.
 *
 * `HOE-C04` and `HOE-B04` both require the checkpoint to be "a product object"
 * whose primary action is Resume — explicitly not "new chat". The distinction
 * matters because a surface whose most prominent control starts over is a
 * surface that quietly discards long-horizon work; the operator learns that
 * continuing is the awkward path.
 *
 * [primaryAction] is therefore computed, not chosen by the renderer. A view
 * that decided for itself could put New alongside Resume with equal weight,
 * which is the same failure with extra steps.
 */
data class CheckpointSummary(
    val goalId: String,
    val nodeId: String?,
    val phase: String?,
    val recordedAt: Instant,
    /** Whether durable state is actually resumable right now. */
    val resumable: Boolean,
    val evidenceCount: Int,
    val nextAction: String?
) {
    fun ageAt(now: Instant): Duration = Duration.between(recordedAt, now)

    /**
     * The one action the surface should present most prominently.
     *
     * Resume whenever the checkpoint is resumable. When it is not, the honest
     * primary is inspection — not "start a new run", which would discard the
     * unresumable state without ever showing the operator why it could not
     * continue.
     */
    val primaryAction: CheckpointAction
        get() = if (resumable) CheckpointAction.RESUME else CheckpointAction.INSPECT

    fun render(now: Instant): String =
        "checkpoint goal=$goalId node=${nodeId ?: "none"} phase=${phase ?: "none"} " +
            "age=${ageAt(now).toMinutes()}m evidence=$evidenceCount " +
            "primary=${primaryAction.canonical}"
}

enum class CheckpointAction(val canonical: String, val label: String) {
    RESUME("resume", "Resume"),
    INSPECT("inspect", "Inspect why this cannot resume")
}
