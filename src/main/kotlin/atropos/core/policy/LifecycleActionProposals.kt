/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.util.UUID

/**
 * Builds [ActionProposal]s for daemon and queue lifecycle transitions.
 *
 * Construction only — no verdict.
 *
 * These proposals carry no command and no target paths: they authorise a state
 * change, not an execution. They exist so lifecycle control travels the same
 * road as everything else, rather than being the one authority that answers to
 * itself.
 */
object LifecycleActionProposals {
    /** A daemon lifecycle transition, e.g. `"start"`, `"stop"`, `"poll"`. */
    fun daemon(operation: String, service: String = "daemon"): ActionProposal =
        ActionProposal(
            id = nextId("daemon"),
            actionClass = PolicyActionClass.DAEMON,
            actor = ActionActor.SystemService(service),
            metadata = mapOf("operation" to operation)
        )

    /** A durable-queue transition, e.g. `"enqueue"`, `"lease"`, `"complete"`. */
    fun queue(operation: String, detail: String = "", service: String = "queue"): ActionProposal =
        ActionProposal(
            id = nextId("queue"),
            actionClass = PolicyActionClass.QUEUE,
            actor = ActionActor.SystemService(service),
            metadata = mapOf(
                "operation" to operation,
                "detail" to detail
            )
        )

    private fun nextId(prefix: String): String = "$prefix-" + UUID.randomUUID().toString().take(12)
}
