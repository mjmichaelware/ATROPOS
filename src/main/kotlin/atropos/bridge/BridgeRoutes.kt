/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpResponse
import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpRoute
import atropos.bridge.http.HttpRouteTable
import atropos.bridge.http.HttpStreamRoute
import atropos.bridge.http.JsonWriter
import atropos.bridge.projection.ApprovalProjection
import atropos.bridge.projection.CommandProjection
import atropos.bridge.projection.ProjectProjection
import atropos.bridge.projection.SixAnswersProjection
import atropos.bridge.projection.VocabularyProjection
import atropos.cli.ui.HomeStateProvider
import atropos.core.approval.ApprovalOutcome
import atropos.core.approval.ApprovalSurface
import atropos.core.approval.PendingApprovalStore

/**
 * The read-only route set the engine exposes to its clients.
 *
 * Every route here answers a question. None of them mutate, and that is a
 * boundary rather than a milestone: the existing Next.js bridge already refuses
 * argv passthrough because the CLI can reach `/shell`, `!command` and `/cd`, so
 * an open write surface on a loopback port is remote code execution against the
 * operator's own machine. Widening this set is a deliberate act that belongs
 * with an attribution and approval flow, not a convenience edit.
 *
 * The handlers hold no logic. Each one calls an existing owner and hands the
 * result to a projection — which is what `HOE-C02`'s "no business logic in Web"
 * actually requires: the logic has to be somewhere the Web cannot reimplement.
 */
class BridgeRoutes(
    private val homeState: HomeStateProvider = HomeStateProvider(),
    private val activeProvider: () -> String = { "unknown" },
    private val sixAnswers: SixAnswersProjection = SixAnswersProjection(),
    private val projects: ProjectProjection = ProjectProjection(),
    private val commands: CommandProjection = CommandProjection(),
    private val vocabulary: VocabularyProjection = VocabularyProjection(),
    private val approvals: PendingApprovalStore = PendingApprovalStore(),
    private val approvalView: ApprovalProjection = ApprovalProjection()
) {
    fun table(): HttpRouteTable {
        lateinit var table: HttpRouteTable
        table = HttpRouteTable(
            listOf(
                HttpRoute("GET", "/v1/health", "liveness and engine identity") {
                    HttpResponse.json(
                        JsonWriter.obj(
                            "ok" to JsonWriter.bool(true),
                            "engine" to JsonWriter.str("atropos"),
                            "surface" to JsonWriter.str("bridge")
                        )
                    )
                },
                HttpRoute("GET", "/v1/routes", "the routes this build exposes") {
                    HttpResponse.json(table.describe())
                },
                HttpRoute("GET", "/v1/answers", "the six continuous answers") {
                    HttpResponse.json(sixAnswers.render(capture()))
                },
                HttpRoute("GET", "/v1/projects", "durable project registry") {
                    HttpResponse.json(projects.render(capture()))
                },
                HttpRoute("GET", "/v1/commands", "command registry, palette and help sections") {
                    HttpResponse.json(commands.render())
                },
                HttpRoute("GET", "/v1/vocabulary", "status and completion vocabularies") {
                    HttpResponse.json(vocabulary.render())
                },
                HttpRoute("GET", "/v1/approvals", "actions waiting on a human decision") {
                    HttpResponse.json(approvalView.render(approvals.pending()))
                },
                HttpRoute("POST", "/v1/approvals/decide", "record a human approval decision") { request ->
                    decideApproval(request)
                },
                HttpRoute("GET", "/v1/answers/stream", "six continuous answers, pushed") {
                    // Advertised in /v1/routes and reachable as a stream; this
                    // request-path entry exists so a client that asks without
                    // an event-stream connection is told what it is rather
                    // than getting a 404 for a route that plainly exists.
                    HttpResponse.refusal(
                        400,
                        "stream-required",
                        "/v1/answers/stream is a server-sent event stream.",
                        "Open it with an EventSource, or call GET /v1/answers for a single snapshot."
                    )
                }
            )
        )
        return table
    }

    /**
     * The bridge's only write.
     *
     * It cannot originate an action. It records a human answer to a question
     * policy already asked, and the executor that owns the action is what
     * decides whether to proceed — so the widest thing this route can do is
     * release something the engine had already stopped, or refuse it.
     *
     * Attribution is mandatory. §20.7 forbids a component approving its own
     * proposal, and a decision with no named decider cannot be checked against
     * that rule. The surface is recorded as BRIDGE rather than CLI because a
     * loopback decision is made by whoever holds the machine, which is a weaker
     * claim than an authenticated session and an auditor must be able to tell
     * them apart.
     */
    private fun decideApproval(request: HttpRequest): HttpResponse {
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
                outcome.reason,
                "Call GET /v1/approvals for what is actually pending."
            )
        }
    }

    /** Reads one `key=value` field from a form-encoded body. */
    private fun field(body: String, key: String): String =
        body.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            .orEmpty()

    /**
     * The streaming half of the bridge.
     *
     * Source Doc 4 calls the six answers *continuous*, and a surface that has
     * to poll for them is showing a snapshot with a timestamp it cannot see.
     * This pushes a fresh answer set on an interval and stops the moment the
     * client leaves.
     *
     * It reuses [SixAnswersProjection] rather than shaping its own payload:
     * a stream that disagreed with `GET /v1/answers` would be a second source
     * of truth for the same six questions.
     */
    fun streamRoutes(
        intervalMillis: Long = 2_000,
        maxFrames: Int = Int.MAX_VALUE,
        sleep: (Long) -> Unit = Thread::sleep
    ): List<HttpStreamRoute> = listOf(
        HttpStreamRoute("GET", "/v1/answers/stream", "six continuous answers, pushed") { _, sink ->
            var frames = 0
            // The first frame is sent immediately: a stream that waits one
            // interval before saying anything is indistinguishable from a
            // stream that failed to start.
            while (sink.isOpen() && frames < maxFrames) {
                if (!sink.emit("answers", sixAnswers.render(capture()))) return@HttpStreamRoute
                frames += 1
                if (frames >= maxFrames) return@HttpStreamRoute
                sleep(intervalMillis)
            }
        }
    )

    /**
     * Reads durable state once per request.
     *
     * Deliberately uncached. A cockpit that shows a cached answer is a cockpit
     * that can report a finished run as still working, and §4.1 treats a stale
     * answer presented as current as a fault rather than an optimisation.
     */
    private fun capture() = homeState.capture(activeProvider())
}
