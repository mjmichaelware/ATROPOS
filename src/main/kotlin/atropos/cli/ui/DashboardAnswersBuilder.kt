/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Health
import atropos.core.agent.AgentQueueCheckpoint
import atropos.core.agent.AgentQueueRecord
import atropos.core.agent.AgentQueueState
import atropos.core.project.ProjectRecord
import atropos.core.project.ProjectStatus
import atropos.core.security.RedactionFilter

class DashboardAnswersBuilder(
    private val redactionFilter: RedactionFilter,
    private val taskWidth: Int = 72
) {
    fun objective(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?,
        pending: List<AgentQueueRecord>,
        projects: List<ProjectRecord>?
    ): DashboardRenderer.Answer {
        val stated = projects?.firstOrNull { it.objective.isNotBlank() && !it.status.terminal }
        if (stated != null) {
            return DashboardRenderer.Answer(
                redactionFilter.compact(stated.objective, taskWidth),
                Health.VERIFIED
            )
        }

        return when {
            queue == null -> unreadable()
            active != null -> DashboardRenderer.Answer(task(active), Health.VERIFIED)
            pending.isNotEmpty() -> DashboardRenderer.Answer(task(pending.first()), Health.PENDING)
            projects?.any { !it.status.terminal } == true -> DashboardRenderer.Answer(
                "no objective stated · /project objective <id> <text>",
                Health.PENDING
            )
            projects?.isNotEmpty() == true -> DashboardRenderer.Answer(
                "no active project · /project new <name> <objective>",
                Health.UNKNOWN
            )
            else -> DashboardRenderer.Answer("no objective queued", Health.UNKNOWN)
        }
    }

    fun doing(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?,
        pending: List<AgentQueueRecord>
    ): DashboardRenderer.Answer = when {
        queue == null -> unreadable()
        active != null -> DashboardRenderer.Answer(
            "${active.state.asRunState().label} ${active.id}",
            Health.VERIFIED
        )
        pending.isNotEmpty() -> DashboardRenderer.Answer(
            "idle · ${pending.size} waiting",
            Health.PENDING
        )
        else -> DashboardRenderer.Answer("idle · no running work", Health.UNKNOWN)
    }

    fun why(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?
    ): DashboardRenderer.Answer = when {
        queue == null -> unreadable()
        active == null -> DashboardRenderer.Answer("nothing running", Health.UNKNOWN)
        !active.sourceEvidence.isNullOrBlank() -> DashboardRenderer.Answer(
            redactionFilter.compact(active.sourceEvidence!!, taskWidth),
            Health.VERIFIED
        )
        else -> DashboardRenderer.Answer(
            "no rationale recorded · provider ${active.provider ?: "unassigned"}",
            Health.PENDING
        )
    }

    fun progress(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?,
        failed: Int
    ): DashboardRenderer.Answer {
        if (queue == null) return unreadable()
        if (queue.isEmpty()) return DashboardRenderer.Answer("nothing tracked", Health.UNKNOWN)

        val complete = queue.count { it.state == AgentQueueState.COMPLETED }
        val checkpoint = active?.let { " · ${checkpointReadable(it.checkpoint)}" }.orEmpty()
        val health = when {
            failed > 0 -> Health.ERROR
            complete == queue.size -> Health.VERIFIED
            else -> Health.PENDING
        }
        return DashboardRenderer.Answer("$complete/${queue.size} complete$checkpoint", health)
    }

    fun next(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?,
        pending: List<AgentQueueRecord>,
        failed: List<AgentQueueRecord>
    ): DashboardRenderer.Answer = when {
        queue == null -> DashboardRenderer.Answer(
            "/agent queue list — queue unreadable",
            Health.ERROR
        )
        failed.isNotEmpty() -> DashboardRenderer.Answer(
            "/agent queue show ${failed.first().id} — repair failure",
            Health.ERROR
        )
        active != null -> DashboardRenderer.Answer(
            "/agent status — watch ${active.id}",
            Health.VERIFIED
        )
        pending.any { it.state == AgentQueueState.RETRY_WAIT } -> DashboardRenderer.Answer(
            "/agent queue list — retry backoff pending",
            Health.PENDING
        )
        pending.isNotEmpty() -> DashboardRenderer.Answer(
            "/agent run — start next queued task",
            Health.PENDING
        )
        else -> DashboardRenderer.Answer(
            "/agent queue add <task> — nothing queued",
            Health.UNKNOWN
        )
    }

    fun evidence(queue: List<AgentQueueRecord>?): DashboardRenderer.Answer {
        if (queue == null) return unreadable()

        val linked = queue.count {
            !it.verificationId.isNullOrBlank() || !it.sourceEvidence.isNullOrBlank()
        }
        return if (linked > 0) {
            DashboardRenderer.Answer("$linked linked · .atropos/agent/queue", Health.VERIFIED)
        } else {
            DashboardRenderer.Answer("none recorded · .atropos/agent/queue", Health.UNKNOWN)
        }
    }

    private fun unreadable(): DashboardRenderer.Answer =
        DashboardRenderer.Answer("unreadable · .atropos/agent/queue", Health.ERROR)

    fun task(record: AgentQueueRecord): String =
        redactionFilter.compact(redactionFilter.redact(record.task), taskWidth)

    fun checkpointReadable(checkpoint: AgentQueueCheckpoint): String =
        checkpoint.name.lowercase().replace('_', ' ')

    private fun ProjectStatus.asRunState(): atropos.cli.ui.design.RunState = when (this) {
        ProjectStatus.IDLE -> atropos.cli.ui.design.RunState.IDLE
        ProjectStatus.PLANNING -> atropos.cli.ui.design.RunState.QUEUED
        ProjectStatus.WAITING -> atropos.cli.ui.design.RunState.WAITING
        ProjectStatus.WORKING -> atropos.cli.ui.design.RunState.RUNNING
        ProjectStatus.REVIEW_REQUIRED -> atropos.cli.ui.design.RunState.WAITING
        ProjectStatus.BLOCKED -> atropos.cli.ui.design.RunState.BLOCKED
        ProjectStatus.COMPLETED -> atropos.cli.ui.design.RunState.COMPLETE
        ProjectStatus.FAILED -> atropos.cli.ui.design.RunState.FAILED
        ProjectStatus.CANCELLED -> atropos.cli.ui.design.RunState.CANCELLED
    }

    private fun AgentQueueState.asRunState(): atropos.cli.ui.design.RunState = when (this) {
        AgentQueueState.QUEUED -> atropos.cli.ui.design.RunState.QUEUED
        AgentQueueState.LEASED, AgentQueueState.RUNNING -> atropos.cli.ui.design.RunState.RUNNING
        AgentQueueState.RETRY_WAIT -> atropos.cli.ui.design.RunState.RETRYING
        AgentQueueState.COMPLETED -> atropos.cli.ui.design.RunState.COMPLETE
        AgentQueueState.FAILED, AgentQueueState.CORRUPT -> atropos.cli.ui.design.RunState.FAILED
        AgentQueueState.REFUSED -> atropos.cli.ui.design.RunState.BLOCKED
        AgentQueueState.CANCELLED -> atropos.cli.ui.design.RunState.CANCELLED
    }
}
