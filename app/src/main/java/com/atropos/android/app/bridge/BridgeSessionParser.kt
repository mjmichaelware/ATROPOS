/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import com.atropos.android.app.ui.ChatListEntry
import org.json.JSONObject

/** Parses only the session-list projection needed by the Android surface. */
object BridgeSessionParser {
    fun parse(body: String): List<ChatListEntry> = runCatching {
        val sessions = JSONObject(body).optJSONArray("sessions") ?: return emptyList()
        (0 until sessions.length()).mapNotNull { index ->
            sessions.optJSONObject(index)?.let { row ->
                val id = row.optString("id").takeIf(String::isNotBlank) ?: return@let null
                ChatListEntry(
                    id = id,
                    title = row.optString("title").ifBlank { "New conversation" },
                    updatedAt = row.optString("updatedAt")
                )
            }
        }
    }.getOrDefault(emptyList())
}
