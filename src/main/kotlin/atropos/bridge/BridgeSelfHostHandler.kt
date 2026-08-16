/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.bridge.projection.SelfHostProjection
import atropos.core.agent.SelfHostGoalService

/**
 * Starting, advancing and watching a self-build run from a client surface.
 *
 * `BridgeRoutes` documents its write surface as deliberately narrow, and says
 * widening it "is a decision that belongs with an attribution and approval
 * flow, not a convenience edit". This is that decision made explicitly rather
 * than by accident, and it is bounded in three specific ways.
 *
 * **It accepts a prompt, never a command.** The input is the same natural
 * language `/agent self-host run` takes. There is no argv, no path, no shell
 * string — the restriction that keeps a loopback port from being remote code
 * execution against the operator's own machine is untouched.
 *
 * **It is attributed.** A run mutates the operator's source tree, so
 * `startedBy` is required exactly as `decidedBy` is on an approval decision.
 * A build nobody is named for cannot be audited afterwards, and "which of my
 * surfaces started this" is the first question asked of a change nobody
 * remembers requesting.
 *
 * **Every gate downstream is unchanged.** This calls the same
 * [SelfHostGoalService] the CLI does, so the policy engine, the territory
 * grant, the bounded-agency gate, the compile gate and the patch attestation
 * all still run. Nothing here can approve anything; it can only ask.
 *
 * Advancing is one step per call rather than a loop. The CLI's runner drives
 * many advances because it is attached to a terminal that can be interrupted;
 * a phone on a dropped connection cannot interrupt anything, and a route that
 * ran to completion would leave the operator holding a request they could not
 * stop.
 */
internal class BridgeSelfHostHandler(
    private val service: SelfHostGoalService,
    private val view: SelfHostProjection = SelfHostProjection()
) {

    /**
     * Opens a goal for [prompt] and returns it without advancing.
     *
     * Start and advance are separate calls on purpose. Starting is cheap and
     * decides only what will be attempted; advancing spends a provider call and
     * can write to the tree. A client that wants both makes two requests and is
     * therefore able to show the plan before anything acts on it.
     */
    fun start(request: HttpRequest): HttpResponse {
        val prompt = value(request, "prompt")
        val startedBy = value(request, "startedBy")
        val phase = value(request, "phase").ifBlank { DEFAULT_PHASE }

        if (prompt.isBlank()) {
            return HttpResponse.badRequest(
                "Starting a self-build run needs a 'prompt'.",
                "POST /v1/selfhost/start with {\"prompt\":\"...\",\"startedBy\":\"<who>\"}"
            )
        }
        if (startedBy.isBlank()) {
            return HttpResponse.refusal(
                403,
                "attribution-required",
                "A self-build run must name who asked for it.",
                "Send startedBy=<operator or surface>; an unattributed run cannot be audited."
            )
        }

        val result = service.startGoal(prompt, phase)
        val goalId = result.goal?.record?.id
        if (!result.ok || goalId == null) {
            return HttpResponse.refusal(
                409,
                "selfhost-start-refused",
                result.message,
                "The engine declined to open this goal; its message explains why."
            )
        }

        service.addEvidence(goalId, "bridge_start startedBy=$startedBy phase=$phase")
        return HttpResponse.json(view.renderStart(service.status(goalId), true, result.message))
    }

    /**
     * Runs one advance of an existing goal.
     *
     * Synchronous, like `/v1/queue/run`: the caller asked for work to happen
     * and the reply says what happened, rather than an acknowledgement they
     * would have to poll to interpret.
     */
    fun advance(request: HttpRequest): HttpResponse {
        val goalId = value(request, "goalId")
        if (goalId.isBlank()) {
            return HttpResponse.badRequest(
                "Advancing needs a 'goalId'.",
                "POST /v1/selfhost/advance?goalId=<id>"
            )
        }

        val advanced = service.advanceNextResumableGoal(
            goalId = goalId,
            compactState = "advance requested from a client surface"
        )
        val status = service.status(goalId)

        // A refused advance is still a state a client must see: the goal may
        // have hit a gate, exhausted retries, or completed. Reporting only the
        // refusal would hide which of those it was.
        return if (advanced.ok) {
            HttpResponse.json(view.render(status))
        } else {
            HttpResponse.refusal(
                409,
                "selfhost-advance-refused",
                advanced.message,
                "GET /v1/selfhost/status?goalId=$goalId shows the DAG state behind this refusal."
            )
        }
    }

    /** The current state of a goal, or the most recent one when no id is given. */
    fun status(request: HttpRequest): HttpResponse {
        val goalId = request.query["goalId"].orEmpty().ifBlank { null }
        val status = service.status(goalId)
        if (status.goalId.isBlank()) {
            return HttpResponse.refusal(
                404,
                "selfhost-goal-unknown",
                goalId?.let { "No self-build goal matches '$it'." } ?: "No self-build goal has been started.",
                "Start one with POST /v1/selfhost/start."
            )
        }
        return HttpResponse.json(view.render(status))
    }

    /**
     * Reads a field from the query string or the form-encoded body.
     *
     * Query first so an id can be given either way, matching how the queue and
     * approval routes already behave — a client should not have to know which
     * of two equivalent forms a particular route prefers.
     */
    private fun value(request: HttpRequest, key: String): String =
        request.query[key].orEmpty().ifBlank { field(request.body, key) }.trim()

    private fun field(body: String, key: String): String {
        val jsonMatch = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(body)
        if (jsonMatch != null) {
            return jsonMatch.groupValues[1]
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        // Form encoding, as the approval route accepts. A prompt arrives as
        // JSON in practice because it contains spaces and newlines, but the
        // simpler form must keep working for a caller using curl by hand.
        return body.split('&')
            .firstOrNull { it.substringBefore('=') == key }
            ?.substringAfter('=', "")
            ?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
            .orEmpty()
    }

    private companion object {
        /** Phase 11 is the self-build phase; the CLI defaults to it too. */
        const val DEFAULT_PHASE = "11"
    }
}
