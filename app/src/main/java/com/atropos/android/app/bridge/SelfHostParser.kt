/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import org.json.JSONObject

/**
 * A self-build run's DAG progress.
 *
 * Null rather than zeroes when the engine has not synthesised a graph yet.
 * "No plan has been made" and "a plan with nothing done" look identical as
 * counts and mean opposite things: the first is a run still starting, the
 * second is a run that planned and then achieved nothing.
 */
data class MobileDagProgress(
    val dagId: String,
    val total: Int,
    val complete: Int,
    val failed: Int,
    val blocked: Int,
    val pending: Int,
    val running: Int,
    val message: String
) {
    /** 0.0 to 1.0, or null when there is nothing to be a fraction of. */
    fun fraction(): Double? = if (total <= 0) null else complete.toDouble() / total

    val stalled: Boolean get() = failed > 0 || blocked > 0
}

data class MobileSelfHostRun(
    val goalId: String,
    val status: String,
    val terminalCondition: String?,
    val phase: String?,
    val currentNodeId: String?,
    val dag: MobileDagProgress?,
    val message: String
) {
    val finished: Boolean get() = terminalCondition != null

    /**
     * Finished *and* succeeded. Kept separate from [finished] because a client
     * that polled only for completion would show a terminal failure as a
     * finished build.
     */
    val succeeded: Boolean get() = terminalCondition == "verified_complete"
}

object SelfHostParser {

    fun parse(body: String): MobileSelfHostRun? = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) return null
        val goalId = root.optString("goalId")
        if (goalId.isBlank()) return null
        MobileSelfHostRun(
            goalId = goalId,
            status = root.optString("status", "unknown"),
            terminalCondition = root.optString("terminalCondition").takeIf { it.isNotBlank() && it != "null" },
            phase = root.optString("phase").takeIf { it.isNotBlank() && it != "null" },
            currentNodeId = root.optString("currentNodeId").takeIf { it.isNotBlank() && it != "null" },
            dag = root.optJSONObject("dag")?.let(::progress),
            message = root.optString("message")
        )
    }.getOrNull()

    /**
     * The engine's refusal text, so a client can say why rather than only that.
     *
     * A refusal carries a `detail` and a `remedy`; both are worth showing — the
     * remedy is usually the only actionable half.
     */
    fun refusal(body: String): String = runCatching {
        val root = JSONObject(body)
        listOf(root.optString("detail"), root.optString("remedy"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }.getOrNull().orEmpty()

    private fun progress(json: JSONObject) = MobileDagProgress(
        dagId = json.optString("dagId"),
        total = json.optInt("total"),
        complete = json.optInt("complete"),
        failed = json.optInt("failed"),
        blocked = json.optInt("blocked"),
        pending = json.optInt("pending"),
        running = json.optInt("running"),
        message = json.optString("message")
    )
}
