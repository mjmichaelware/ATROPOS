/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import com.atropos.android.app.ui.MobileMessage
import org.json.JSONObject

/**
 * Reads the bridge's turn payloads.
 *
 * Uses org.json, which is part of Android itself, rather than adding a parsing
 * dependency for two shapes. Both `/v1/messages` and the reply from
 * `/v1/message` carry the same turn objects, so one reader serves both.
 *
 * Malformed input yields an empty list rather than an exception: a client that
 * crashes on an unexpected payload is worse than one that shows nothing and
 * keeps polling.
 */
object BridgeTurnParser {

    fun parse(body: String): List<MobileMessage> = runCatching {
        val root = JSONObject(body)
        when {
            // GET /v1/messages
            root.has("turns") -> {
                val array = root.getJSONArray("turns")
                (0 until array.length()).mapNotNull { turn(array.optJSONObject(it)) }
            }
            // POST /v1/message returns the accepted turn and the reply
            root.has("accepted") || root.has("reply") -> listOfNotNull(
                turn(root.optJSONObject("accepted")),
                turn(root.optJSONObject("reply"))
            )
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /** The human-readable reason from a refusal body, when there is one. */
    fun detail(body: String): String = runCatching {
        val root = JSONObject(body)
        root.optString("detail").ifBlank { root.optString("reason") }
    }.getOrDefault("")

    private fun turn(json: JSONObject?): MobileMessage? {
        if (json == null) return null
        val id = json.optString("id").ifBlank { return null }
        return MobileMessage(
            id = id,
            text = json.optString("text"),
            isUser = json.optString("author") == "operator",
            timestamp = System.currentTimeMillis()
        )
    }
}
