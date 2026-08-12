/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

/**
 * Turns an operator message into the engine's reply.
 *
 * An interface rather than a concrete call into the CLI router, because the
 * router renders into a terminal engine and answering a phone must not require
 * a terminal. Keeping this seam also means the bridge can be tested without
 * starting a provider or touching a repository.
 *
 * Implementations must not throw: a responder that fails has to say so as a
 * reply, or the operator sees a message vanish with no turn at all.
 */
fun interface BridgeConversationResponder {
    fun reply(message: String): String
}

/**
 * The reply used until a real execution path is wired in.
 *
 * It states plainly that the message was received and not executed. The
 * alternative — echoing something that sounds like an answer — would let a
 * client look functional while nothing ran, which is the failure mode the
 * completion rules exist to prevent.
 */
class UnwiredConversationResponder(
    private val surface: String = "bridge"
) : BridgeConversationResponder {
    override fun reply(message: String): String =
        "Received on the $surface surface (${message.length} chars). " +
            "Execution is not wired to this surface yet, so nothing was run. " +
            "Use the CLI for execution; this transcript is live."
}
