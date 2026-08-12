/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import org.json.JSONObject

data class MobileThinking(val depth: Int, val hasMore: Boolean, val lines: List<String>)

object ThinkingParser {
    fun parse(body: String): MobileThinking? = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("present")) return null
        val values = root.optJSONArray("lines")
        val lines = if (values == null) emptyList() else {
            (0 until values.length()).mapNotNull { values.optJSONObject(it)?.optString("text") }
        }
        MobileThinking(root.optInt("depth", 1), root.optBoolean("hasMore"), lines)
    }.getOrNull()
}
