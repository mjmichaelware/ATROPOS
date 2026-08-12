/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

import java.time.Instant

/** Who produced a turn. */
enum class TurnAuthor { OPERATOR, ENGINE }

/**
 * One turn of a bridge conversation.
 *
 * The transcript is the thing an Android or web client renders, so a turn
 * carries only what a client needs to draw and order it. Anything about *how*
 * the engine produced a reply is evidence and belongs on the evidence surface,
 * not inlined here where every client would have to understand it.
 */
data class BridgeConversationTurn(
    val id: String,
    val author: TurnAuthor,
    val text: String,
    val at: Instant
)
