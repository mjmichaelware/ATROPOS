package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.http.JsonWriter
import atropos.core.approval.ApprovalOutcome
import atropos.core.approval.ApprovalSurface
import atropos.core.approval.PendingApprovalStore
import atropos.core.security.RedactionFilter

internal class BridgeApprovalHandler(
    private val approvals: PendingApprovalStore,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun decideApproval(request: HttpRequest): HttpResponse {
        val id = request.query["id"].orEmpty().ifBlank { field(request.body, "id") }
        val decidedBy = request.query["decidedBy"].orEmpty().ifBlank { field(request.body, "decidedBy") }
        val approved = (request.query["approved"].orEmpty().ifBlank { field(request.body, "approved") })
            .toBooleanStrictOrNull()

        if (id.isBlank() || approved == null) {
            return HttpResponse.badRequest(
                "An approval decision needs an 'id' and an 'approved' boolean.",
                "POST /v1/approvals/decide?id=<id>&approved=true&decidedBy=<who>"
            )
        }
        if (decidedBy.isBlank()) {
            return HttpResponse.refusal(
                403,
                "attribution-required",
                "An approval decision must name who made it.",
                "Send decidedBy=<operator>; an unattributed decision cannot be audited."
            )
        }

        return when (val outcome = approvals.decide(id, approved, decidedBy, ApprovalSurface.BRIDGE)) {
            is ApprovalOutcome.Recorded -> HttpResponse.json(
                JsonWriter.obj(
                    "ok" to JsonWriter.bool(true),
                    "id" to JsonWriter.str(outcome.approval.id),
                    "approved" to JsonWriter.bool(approved)
                )
            )
            is ApprovalOutcome.Refused -> HttpResponse.refusal(
                409,
                "approval-refused",
                redactionFilter.compact(outcome.reason),
                "Call GET /v1/approvals for what is actually pending."
            )
        }
    }

    private fun field(body: String, key: String): String =
        body.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            .orEmpty()
}
