/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.agent.SelfHostStatus
import atropos.core.security.RedactionFilter

/**
 * A self-build run, on the wire.
 *
 * The client surfaces could watch the engine and talk to it, and could not ask
 * it to build anything — the one thing the engine exists to do. This is the
 * read half of closing that: what a goal is doing, how far its DAG has got, and
 * whether it finished or stopped.
 *
 * Every count is rendered as measured. A goal whose DAG has not been synthesised
 * yet emits `dag: null` rather than zeroes, because "no graph yet" and "a graph
 * with nothing done" are different situations and only one of them means the
 * planner failed.
 *
 * A goal's own prompt is not echoed here. It is operator text that may name
 * paths or paste a key, and it is already durable in the goal record; sending
 * it back over a port would put it in a second place with a second lifetime.
 */
class SelfHostProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    fun render(status: SelfHostStatus): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "goalId" to JsonWriter.str(status.goalId),
        "status" to JsonWriter.str(status.status.name.lowercase()),
        // Absent until the run reaches an end state. Null is the honest value:
        // a run still going has no terminal condition, and rendering one as
        // empty text invites a client to treat it as a result.
        "terminalCondition" to (status.terminalCondition?.let { JsonWriter.str(it.name.lowercase()) } ?: "null"),
        "phase" to (status.phase?.let(JsonWriter::str) ?: "null"),
        "currentNodeId" to (status.currentNodeId?.let(JsonWriter::str) ?: "null"),
        "dag" to (status.dagStatus?.let(::dag) ?: "null"),
        "message" to JsonWriter.str(redact(status.message))
    )

    /**
     * Whether the run is finished, and whether a client may advance it again.
     *
     * `finished` is not the same as `succeeded`. A client that polled only for
     * completion would show a terminal failure as a finished build.
     */
    fun renderStart(status: SelfHostStatus, started: Boolean, message: String): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(started),
        "goalId" to JsonWriter.str(status.goalId),
        "status" to JsonWriter.str(status.status.name.lowercase()),
        "message" to JsonWriter.str(redact(message))
    )

    private fun dag(status: atropos.core.dag.DagStatus): String = JsonWriter.obj(
        "dagId" to JsonWriter.str(status.dagId),
        "total" to JsonWriter.num(status.totalNodes.toLong()),
        "complete" to JsonWriter.num(status.completedNodes.toLong()),
        "failed" to JsonWriter.num(status.failedNodes.toLong()),
        "blocked" to JsonWriter.num(status.blockedNodes.toLong()),
        "pending" to JsonWriter.num(status.pendingNodes.toLong()),
        "running" to JsonWriter.num(status.runningNodes.toLong()),
        "ready" to JsonWriter.strArr(status.readyNodes),
        "message" to JsonWriter.str(redact(status.message))
    )

    private fun redact(value: String): String = redactionFilter.redact(value)
}
