/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import org.json.JSONObject

/**
 * Reads what `POST /v1/command` and `GET /v1/command/allowed` returned.
 *
 * The engine's output is passed through unchanged. A client that reformatted
 * it would be reimplementing the CLI's presentation on the other side of a
 * socket, and the two surfaces would start describing the same run
 * differently — which is the drift the shared command path exists to prevent.
 */
object CommandParser {

    fun parse(body: String): CommandOutcome? = runCatching {
        val root = JSONObject(body)
        val command = root.optString("command")
        if (!root.optBoolean("ok", false)) {
            // `ok:false` with a `failure` is a command that ran and threw —
            // distinct from an HTTP refusal, which never reached the router.
            val failure = root.optString("failure").ifBlank { "the command failed" }
            return CommandOutcome.Refused(failure)
        }
        CommandOutcome.Ran(command = command, output = root.optString("output"))
    }.getOrNull()

    fun allowedFamilies(body: String): List<String> = runCatching {
        val array = JSONObject(body).optJSONArray("families") ?: return emptyList()
        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())
}
