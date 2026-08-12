/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.approval

import java.time.Instant

/**
 * One action the policy engine refused to run without a human decision.
 *
 * `AgencyDisposition.APPROVAL_REQUIRED` already existed, but nothing recorded
 * it: the disposition was returned to the caller and lost. That left every
 * surface unable to answer "what is waiting on me", and made an approval card
 * impossible to render from anything real.
 *
 * The record carries what a human needs in order to decide — who asked, what
 * they want to do, which paths it touches, and why policy stopped it. It
 * deliberately does not carry the action's payload: a diff or a shell string is
 * the thing being judged, not the thing being stored, and copying it here would
 * put model-authored bytes into a second durable location outside the patch
 * store's redaction discipline.
 */
data class PendingApproval(
    val id: String,
    /** The proposal this decision releases, so the executor can match it. */
    val proposalId: String,
    /** Role and node id of whoever asked, e.g. `patch:patch-2026…`. */
    val actor: String,
    /** The typed operation, never raw prose. */
    val operation: String,
    /** Paths the action declared. Empty means it declared none, not "all". */
    val territory: List<String>,
    /** Why policy would not allow it unattended. */
    val reason: String,
    val requestedAt: Instant,
    val decision: ApprovalDecision? = null
) {
    val isPending: Boolean get() = decision == null
}

/**
 * A human's answer, and who gave it.
 *
 * `decidedBy` is not decoration. §20.7 forbids a component approving its own
 * proposal, and a decision with no attributed decider cannot be checked against
 * that rule — it would let the requesting process record its own approval and
 * look identical to a human's.
 */
data class ApprovalDecision(
    val approved: Boolean,
    /** Identity of the decider, and the surface they used. */
    val decidedBy: String,
    val surface: ApprovalSurface,
    val decidedAt: Instant,
    val note: String? = null
)

/**
 * Where a decision came from.
 *
 * Recorded because the surfaces do not carry equal authority. A loopback
 * bridge decision is made by whoever holds the machine, which is a weaker
 * claim than a CLI decision typed into an authenticated session, and a future
 * auditor has to be able to tell them apart rather than inferring it.
 */
enum class ApprovalSurface {
    CLI,
    BRIDGE
}
