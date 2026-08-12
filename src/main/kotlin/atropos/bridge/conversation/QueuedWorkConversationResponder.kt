/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.conversation

/**
 * The seam through which a conversation turn becomes real work.
 *
 * Narrower than the queue service on purpose. The responder needs to enqueue a
 * task and learn its identity; it has no business listing, resolving, running
 * or cancelling work, and a wider dependency would let it grow those. The
 * production binding is [atropos.core.agent.AgentQueueService.enqueue].
 */
fun interface ConversationWorkQueue {
    /** Accepts [task] and returns the durable id it was recorded under. */
    fun enqueue(task: String): String
}

/**
 * Turns an operator message into bounded, durable work.
 *
 * A message from a phone is not answered by calling a provider directly. That
 * would put an unbounded, unverified, unattributed execution path behind an
 * HTTP endpoint, bypassing the queue's attempt limits, the policy gate and the
 * evidence trail that every other execution route goes through. Instead the
 * message is enqueued exactly as CLI-originated work is, and the reply names
 * the record so the operator can follow it.
 *
 * Slash commands are refused rather than half-executed. The command surface is
 * owned by the CLI router, which renders into a terminal; pretending to run
 * `/status` here would mean reimplementing it, which is the duplication the
 * architecture forbids. Saying so plainly is better than a second, divergent
 * implementation of the same command.
 */
class QueuedWorkConversationResponder(
    private val queue: ConversationWorkQueue,
    private val maxTaskChars: Int = 2_000
) : BridgeConversationResponder {

    override fun reply(message: String): String {
        val task = message.trim()
        if (task.isEmpty()) return "Nothing to do: the message was empty."

        if (task.startsWith("/")) {
            return "Slash commands run on the CLI surface, not this one. " +
                "Send what you want done in plain language and it will be queued as work."
        }

        if (task.length > maxTaskChars) {
            return "That message is ${task.length} characters; the queue accepts up to $maxTaskChars. " +
                "Send a shorter instruction, or split it into separate requests."
        }

        return runCatching { queue.enqueue(task) }
            .fold(
                onSuccess = { id ->
                    "Queued as $id. It runs under the same attempt limits, policy gate and " +
                        "evidence trail as work started from the CLI. " +
                        "Follow it with `/agent queue` there, or ask here for its status."
                },
                onFailure = { failure ->
                    // Refusals from the policy gate arrive as exceptions and are
                    // the operator's answer, not an internal error to swallow.
                    val detail = failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName
                    "The engine did not accept that as work: $detail"
                }
            )
    }
}
