/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import org.json.JSONObject

/**
 * One action the engine has stopped on, waiting for a person.
 *
 * @param territory the paths the action declared. Empty means it declared
 *   none — never "every path". The engine's projection is careful about that
 *   distinction and the client must not collapse it, because an approval card
 *   showing a blank territory as "unrestricted" would have a person authorise
 *   the widest possible action by reading it as the narrowest.
 */
data class MobileApproval(
    val id: String,
    val proposalId: String,
    val actor: String,
    val operation: String,
    val territory: List<String>,
    val reason: String,
    val requestedAt: String,
    val pending: Boolean
)

/**
 * Reads `GET /v1/approvals`.
 *
 * Returns an empty list — never a fabricated entry — when the body cannot be
 * read. A client that invented an approval would be asking a person to
 * authorise something the engine never proposed.
 */
object ApprovalParser {

    fun parse(body: String): List<MobileApproval> = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) return emptyList()
        val array = root.optJSONArray("pending") ?: return emptyList()
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::approval)
        }
    }.getOrDefault(emptyList())

    private fun approval(json: JSONObject): MobileApproval {
        val territoryArray = json.optJSONArray("territory")
        val territory = if (territoryArray == null) emptyList() else {
            (0 until territoryArray.length()).map { territoryArray.optString(it) }
                .filter { it.isNotBlank() }
        }
        return MobileApproval(
            id = json.optString("id"),
            proposalId = json.optString("proposalId"),
            actor = json.optString("actor"),
            operation = json.optString("operation"),
            territory = territory,
            reason = json.optString("reason"),
            requestedAt = json.optString("requestedAt"),
            // Absent defaults to true: an entry returned by the pending
            // endpoint is pending unless it says otherwise, and defaulting to
            // false would silently drop a decision the operator owes.
            pending = json.optBoolean("pending", true)
        )
    }
}
