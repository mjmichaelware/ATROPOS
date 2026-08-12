/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import java.time.Instant

/**
 * One conversation, as a chat list needs to show it.
 *
 * The title is derived from the first operator turn rather than asked for.
 * Demanding a name before a question can be typed is friction at exactly the
 * wrong moment, and an untitled row in a list is useless.
 */
data class BridgeSession(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val turnCount: Int
) {
    companion object {
        const val UNTITLED = "New conversation"
        private const val TITLE_MAX = 60

        /**
         * A one-line title from the first thing the operator said. Newlines are
         * collapsed because a list row is one line and a pasted multi-line
         * prompt would otherwise break the layout of every row after it.
         */
        fun titleFrom(firstMessage: String): String {
            val flat = firstMessage.replace(Regex("\\s+"), " ").trim()
            if (flat.isEmpty()) return UNTITLED
            return if (flat.length <= TITLE_MAX) flat else flat.take(TITLE_MAX - 1).trimEnd() + "…"
        }
    }
}
