/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.project.ProjectRecord
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus

/**
 * `/project` — the CLI face of the durable project registry.
 *
 * Source Document 4 §2.2 makes the project the organizational boundary and
 * requires its identity to survive restarts; §3.2 puts objectives above
 * implementation detail. This handler is the surface that lets an operator
 * state an objective, move a project through the §3.3 vocabulary, and read
 * back the permanent history — without leaving the terminal.
 *
 * It only ever displays what the registry holds. Where a field is empty, it
 * says so rather than substituting something plausible.
 */
class ProjectCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val registry: ProjectRegistry = ProjectRegistry(),
    private val invalid: (String) -> AgentCommandOutcome.Invalid = { AgentCommandOutcome.Invalid(it) }
) {
    fun execute(args: List<String>): AgentCommandOutcome {
        return when (args.getOrNull(0)?.lowercase()) {
            null, "list" -> list()
            "new" -> create(args)
            "show" -> show(args)
            "status" -> status(args)
            "objective" -> objective(args)
            "history" -> history(args)
            else -> invalid(USAGE)
        }
    }

    private fun list(): AgentCommandOutcome {
        val projects = registry.list()
        if (projects.isEmpty()) {
            // An empty registry is a real, nominal answer — and the next
            // action is stated so the operator is never left guessing (§0.1).
            val text = "no projects yet\n  next: /project new <name> [objective]"
            ui.renderNotice(AgentCommandText.formatBlock("PROJECTS", text))
            return AgentCommandOutcome.Completed(text)
        }

        val text = projects.joinToString("\n") { project ->
            buildString {
                append(project.id)
                append("  ")
                append(project.status.canonical.padEnd(16))
                append(project.name)
                if (!project.completionIsVerifiable) {
                    // §3.4: a completion nothing corroborates is shown as such.
                    append("  [completed without evidence]")
                }
            }
        }
        ui.renderNotice(AgentCommandText.formatBlock("PROJECTS", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun create(args: List<String>): AgentCommandOutcome {
        val name = args.getOrNull(1) ?: return invalid("usage: /project new <name> [objective]")
        val objective = args.drop(2).joinToString(" ").trim()
        val result = registry.register(name = name, objective = objective)

        val text = buildString {
            appendLine(if (result.created) "created ${result.record.id}" else "already exists: ${result.record.id}")
            appendLine("name       ${result.record.name}")
            appendLine(objectiveLine(result.record))
            append("status     ${result.record.status.canonical}")
        }
        ui.renderNotice(AgentCommandText.formatBlock("PROJECT", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun show(args: List<String>): AgentCommandOutcome {
        val id = args.getOrNull(1) ?: return invalid("usage: /project show <project-id>")
        val project = registry.resolve(id) ?: return invalid("project not found: $id")

        val text = buildString {
            appendLine("id         ${project.id}")
            appendLine("name       ${project.name}")
            appendLine(objectiveLine(project))
            appendLine("status     ${project.status.canonical}")
            appendLine("repo       ${project.binding.repoRoot}")
            appendLine("branch     ${project.binding.branch.ifBlank { "unknown" }}")
            appendLine("work       ${countOrNone(project.workItemIds.size, "item")}")
            appendLine("evidence   ${countOrNone(project.evidenceIds.size, "link")}")
            if (!project.completionIsVerifiable) {
                appendLine("warning    completed with no linked evidence — this claim cannot be verified")
            }
            append("history    ${countOrNone(registry.history(project.id).size, "event")}")
        }
        ui.renderNotice(AgentCommandText.formatBlock("PROJECT", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun status(args: List<String>): AgentCommandOutcome {
        val id = args.getOrNull(1) ?: return invalid(STATUS_USAGE)
        val requested = args.getOrNull(2) ?: return invalid(STATUS_USAGE)
        val project = registry.resolve(id) ?: return invalid("project not found: $id")
        val status = ProjectStatus.fromCanonical(requested)
            ?: return invalid(
                "unknown status '$requested'. Valid: " +
                    ProjectStatus.entries.joinToString(", ") { it.canonical }
            )

        val updated = registry.setStatus(project, status)
        val text = "${project.status.canonical} -> ${updated.status.canonical}"
        ui.renderNotice(AgentCommandText.formatBlock("PROJECT STATUS", text))
        return AgentCommandOutcome.Completed(text)
    }

    private fun objective(args: List<String>): AgentCommandOutcome {
        val id = args.getOrNull(1) ?: return invalid("usage: /project objective <project-id> <text>")
        val text = args.drop(2).joinToString(" ").trim()
        if (text.isBlank()) return invalid("usage: /project objective <project-id> <text>")
        val project = registry.resolve(id) ?: return invalid("project not found: $id")

        val updated = registry.update(
            project.copy(objective = text),
            event = "objective_set",
            message = "objective set"
        )
        val rendered = objectiveLine(updated)
        ui.renderNotice(AgentCommandText.formatBlock("PROJECT OBJECTIVE", rendered))
        return AgentCommandOutcome.Completed(rendered)
    }

    private fun history(args: List<String>): AgentCommandOutcome {
        val id = args.getOrNull(1) ?: return invalid("usage: /project history <project-id>")
        registry.resolve(id) ?: return invalid("project not found: $id")

        val events = registry.history(id)
        val text = if (events.isEmpty()) {
            "no history recorded"
        } else {
            events.joinToString("\n") { event ->
                "${event.timestamp}  ${event.event.padEnd(18)}${event.actor.padEnd(12)}${event.message}"
            }
        }
        ui.renderNotice(AgentCommandText.formatBlock("PROJECT HISTORY", text))
        return AgentCommandOutcome.Completed(text)
    }

    /** An unstated objective is reported as unstated, never filled in. */
    private fun objectiveLine(project: ProjectRecord): String =
        "objective  " + project.objective.ifBlank { "not stated — /project objective ${project.id} <text>" }

    private fun countOrNone(count: Int, noun: String): String =
        if (count == 0) "none" else "$count $noun${if (count == 1) "" else "s"}"

    private companion object {
        const val USAGE = "usage: /project [list|new|show|status|objective|history]"
        const val STATUS_USAGE = "usage: /project status <project-id> <idle|planning|waiting|working|review-required|blocked|completed|failed|cancelled>"
    }
}
