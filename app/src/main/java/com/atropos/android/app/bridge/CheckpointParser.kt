/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import org.json.JSONObject

data class MobileCheckpoint(
    val goalId: String,
    val phase: String,
    val primaryAction: String,
    val actions: List<String>
)

object CheckpointParser {
    fun parse(body: String): MobileCheckpoint? = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("present")) return null
        val primary = root.optJSONObject("primaryAction")?.optString("id").orEmpty()
        val actionArray = root.optJSONArray("actions")
        val actions = if (actionArray == null) emptyList() else {
            (0 until actionArray.length()).mapNotNull { actionArray.optJSONObject(it)?.optString("id") }
        }
        MobileCheckpoint(
            goalId = root.optString("goalId"),
            phase = root.optString("phase"),
            primaryAction = primary,
            actions = actions
        )
    }.getOrNull()
}
