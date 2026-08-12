/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.menu

import atropos.bridge.projection.MenuAction

/**
 * What a graphical client can actually do, as menu entries.
 *
 * Deliberately smaller than the CLI's command set, and that is the design
 * rather than a shortfall. The CLI can reach `/shell`, `!command` and `/cd`;
 * those must never appear on a surface reachable over a port, because
 * presenting them would mean the bridge had to execute them. Every entry here
 * maps to a route that already exists and is already bounded.
 *
 * Each action names the route it invokes so a client never constructs an
 * endpoint from a label. A client that guessed URLs would break silently the
 * first time a route was renamed; one that is told the route breaks loudly, at
 * the point of the rename, in the route table.
 */
object BridgeMenuCatalog {

    const val CONVERSATION = "Conversation"
    const val WORK = "Work"
    const val STATUS = "Status"
    const val GOVERNANCE = "Governance"

    fun actions(): List<MenuAction> = listOf(
        MenuAction(
            id = "session.new",
            label = "New conversation",
            group = CONVERSATION,
            description = "Start a fresh conversation. Nothing is carried over.",
            mutating = true,
            argumentHint = null
        ),
        MenuAction(
            id = "session.resume",
            label = "Resume last conversation",
            group = CONVERSATION,
            description = "Reopen the most recent conversation. Never happens on its own.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "session.list",
            label = "All conversations",
            group = CONVERSATION,
            description = "Every conversation this engine is holding.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "queue.list",
            label = "Queued work",
            group = WORK,
            description = "Work waiting, running or finished, with attempts and failure reasons.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "queue.run",
            label = "Run next queued item",
            group = WORK,
            description = "Advance the next eligible entry. Reports what happened.",
            mutating = true,
            argumentHint = null
        ),
        MenuAction(
            id = "queue.cancel",
            label = "Cancel queued item",
            group = WORK,
            description = "Stop a queue entry that has not finished.",
            mutating = true,
            argumentHint = "queue id"
        ),
        MenuAction(
            id = "status.answers",
            label = "Status",
            group = STATUS,
            description = "The six continuous answers: what is happening and what is next.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "status.checkpoint",
            label = "Checkpoint",
            group = STATUS,
            description = "The resume point, and whether it can be continued.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "status.projects",
            label = "Projects",
            group = STATUS,
            description = "The durable project registry.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "status.activity",
            label = "Activity",
            group = STATUS,
            description = "Recent engine activity.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "governance.approvals",
            label = "Approvals",
            group = GOVERNANCE,
            description = "Actions waiting on a human decision.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "governance.proposals",
            label = "Proposals",
            group = GOVERNANCE,
            description = "Self-improvement proposals and their cooldowns.",
            mutating = false,
            argumentHint = null
        ),
        MenuAction(
            id = "governance.evidence",
            label = "Evidence exports",
            group = GOVERNANCE,
            description = "Where produced artifacts and evidence landed.",
            mutating = false,
            argumentHint = null
        )
    )

    /** The route an action invokes. Clients never build these themselves. */
    fun routeFor(actionId: String): MenuRoute? = when (actionId) {
        "session.new" -> MenuRoute("POST", "/v1/sessions")
        "session.resume" -> MenuRoute("GET", "/v1/sessions/recent")
        "session.list" -> MenuRoute("GET", "/v1/sessions")
        "queue.list" -> MenuRoute("GET", "/v1/queue")
        "queue.run" -> MenuRoute("POST", "/v1/queue/run")
        "queue.cancel" -> MenuRoute("POST", "/v1/queue/cancel")
        "status.answers" -> MenuRoute("GET", "/v1/answers")
        "status.checkpoint" -> MenuRoute("GET", "/v1/checkpoint")
        "status.projects" -> MenuRoute("GET", "/v1/projects")
        "status.activity" -> MenuRoute("GET", "/v1/activity")
        "governance.approvals" -> MenuRoute("GET", "/v1/approvals")
        "governance.proposals" -> MenuRoute("GET", "/v1/proposals")
        "governance.evidence" -> MenuRoute("GET", "/v1/exports")
        else -> null
    }
}

data class MenuRoute(val method: String, val path: String)
