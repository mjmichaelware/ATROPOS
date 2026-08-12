/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.approval.PendingApproval
import atropos.core.security.RedactionFilter

/**
 * Projects what is waiting on a human onto the wire.
 *
 * `HOE-C05` requires approval cards, and a card is only honest if it shows the
 * operator what they are actually releasing: who asked, what operation, which
 * paths, and why policy stopped it. A card that said only "approve?" would be
 * asking for consent without disclosure.
 *
 * The action's payload is deliberately absent — the store does not carry it,
 * and putting a model-authored diff on this wire would move those bytes outside
 * the patch store's redaction discipline.
 */
class ApprovalProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun render(pending: List<PendingApproval>): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "pending" to JsonWriter.arr(pending.map(::approval))
    )

    private fun approval(approval: PendingApproval): String = JsonWriter.obj(
        "id" to JsonWriter.str(approval.id),
        "proposalId" to JsonWriter.str(approval.proposalId),
        "actor" to JsonWriter.str(redact(approval.actor)),
        "operation" to JsonWriter.str(redact(approval.operation)),
        // Empty means the action declared no territory — never "all paths".
        "territory" to JsonWriter.strArr(approval.territory.map(::redact)),
        "reason" to JsonWriter.str(redact(approval.reason)),
        "requestedAt" to JsonWriter.str(approval.requestedAt.toString()),
        "pending" to JsonWriter.bool(approval.isPending)
    )

    private fun redact(value: String): String = redactionFilter.redact(value)
}
