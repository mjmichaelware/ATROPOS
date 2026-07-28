/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Breakpoint
import atropos.cli.ui.design.Role
import atropos.cli.ui.design.Spacing

/**
 * ATROPOS HOE (Human Operating Environment) dashboard.
 * Presents the six continuous answers and operative cockpit at CLI startup.
 */
class DashboardRenderer(
    private val theme: TerminalTheme
) {
    data class ProjectMetrics(
        val name: String,
        val status: String,
        val progress: Int,
        val taskCount: Int,
        val goalCount: Int
    )

    data class WorkItem(
        val id: String,
        val title: String,
        val status: String,
        val priority: String
    )

    data class DashboardState(
        val activeProjects: List<ProjectMetrics> = emptyList(),
        val runningWork: List<WorkItem> = emptyList(),
        val pendingApprovals: Int = 0,
        val queuedItems: Int = 0,
        val failedItems: Int = 0,
        val providerHealth: String = "unknown",
        val memoryUsage: Int = 0
    )

    fun render(state: DashboardState, width: Int): List<String> {
        val safeWidth = width.coerceIn(40, 200)
        val bp = Breakpoint.of(safeWidth)
        val output = mutableListOf<String>()

        output += theme.surface.sectionHeading("ATROPOS", safeWidth, Role.BRAND)
        output += ""

        // Six continuous answers
        output += theme.surface.sectionHeading("Questions", safeWidth, Role.BRAND)
        output += renderSixAnswers(safeWidth)
        output += ""

        // Projects summary
        if (state.activeProjects.isNotEmpty()) {
            output += theme.surface.sectionHeading("Projects", safeWidth, Role.BRAND)
            output += renderProjects(state.activeProjects, safeWidth)
            output += ""
        }

        // Work summary
        if (state.runningWork.isNotEmpty()) {
            output += theme.surface.sectionHeading("Running", safeWidth, Role.BRAND)
            output += renderWork(state.runningWork, safeWidth, bp)
            output += ""
        }

        // Queue status
        output += theme.surface.sectionHeading("Status", safeWidth, Role.BRAND)
        output += renderQueue(
            running = state.runningWork.size,
            queued = state.queuedItems,
            failed = state.failedItems,
            approvals = state.pendingApprovals,
            safeWidth
        )
        output += ""

        // System health
        output += theme.surface.sectionHeading("System", safeWidth, Role.BRAND)
        output += renderSystemHealth(state, safeWidth)

        return output
    }

    private fun renderSixAnswers(width: Int): List<String> {
        val output = mutableListOf<String>()
        // The six questions from ATROPOS HOE:
        // 1. What am I working on? → Current project
        // 2. What's my next action? → Top goal/task
        // 3. What's blocking me? → Approvals/errors
        // 4. Who's involved? → Agents/team
        // 5. What evidence do I have? → Recent artifacts
        // 6. What changed? → Recent activity

        output += theme.surface.row("Working on", "No project selected", width)
        output += theme.surface.row("Next action", "Create or select project", width)
        output += theme.surface.row("Blocked by", "No approvals pending", width)
        output += theme.surface.row("Team", "0 agents assigned", width)
        output += theme.surface.row("Evidence", "0 artifacts", width)
        output += theme.surface.row("Changed", "No activity yet", width)

        return output
    }

    private fun renderProjects(projects: List<ProjectMetrics>, width: Int): List<String> {
        val output = mutableListOf<String>()
        for (project in projects.take(3)) {
            val statusRole = when (project.status.lowercase()) {
                "active" -> Role.STATUS_RUNNING
                "completed" -> Role.STATUS_COMPLETE
                "failed" -> Role.STATUS_FAILED
                else -> Role.STATUS_IDLE
            }
            val statusPainted = theme.paint(statusRole, project.status)
            output += theme.surface.row(
                project.name,
                "$statusPainted ${project.progress}% [${project.taskCount}T/${project.goalCount}G]",
                width
            )
        }
        return output
    }

    private fun renderWork(work: List<WorkItem>, width: Int, bp: Breakpoint): List<String> {
        val output = mutableListOf<String>()
        for (item in work.take(bp.maxWorkItems())) {
            val role = when (item.status.lowercase()) {
                "running" -> Role.STATUS_RUNNING
                "waiting" -> Role.STATUS_WAITING
                else -> Role.STATUS_IDLE
            }
            val statusPainted = theme.paint(role, item.status)
            output += theme.surface.row(
                TerminalText.ellipsize(item.title, Spacing.LABEL_WIDTH),
                "$statusPainted ${item.priority}",
                width
            )
        }
        return output
    }

    private fun renderQueue(running: Int, queued: Int, failed: Int, approvals: Int, width: Int): List<String> {
        val output = mutableListOf<String>()
        output += theme.surface.statusRow(
            "Running",
            running.toString(),
            if (running > 0) atropos.cli.ui.design.Health.VERIFIED else atropos.cli.ui.design.Health.UNKNOWN,
            width
        )
        output += theme.surface.statusRow(
            "Queued",
            queued.toString(),
            if (queued > 0) atropos.cli.ui.design.Health.PENDING else atropos.cli.ui.design.Health.VERIFIED,
            width
        )
        if (failed > 0) {
            output += theme.surface.statusRow(
                "Failed",
                failed.toString(),
                atropos.cli.ui.design.Health.ERROR,
                width
            )
        }
        if (approvals > 0) {
            output += theme.surface.statusRow(
                "Approvals",
                approvals.toString(),
                atropos.cli.ui.design.Health.PENDING,
                width
            )
        }
        return output
    }

    private fun renderSystemHealth(state: DashboardState, width: Int): List<String> {
        val output = mutableListOf<String>()
        val providerHealth = when (state.providerHealth.lowercase()) {
            "healthy" -> atropos.cli.ui.design.Health.VERIFIED
            "degraded" -> atropos.cli.ui.design.Health.PENDING
            "unhealthy" -> atropos.cli.ui.design.Health.ERROR
            else -> atropos.cli.ui.design.Health.UNKNOWN
        }
        output += theme.surface.statusRow("Provider", state.providerHealth, providerHealth, width)
        output += theme.surface.row("Memory", "${state.memoryUsage}%", width)
        return output
    }

    private fun Breakpoint.maxWorkItems(): Int = when (this) {
        Breakpoint.COMPACT -> 2
        Breakpoint.MEDIUM -> 4
        Breakpoint.WIDE -> 6
        Breakpoint.ULTRA -> 8
    }
}
