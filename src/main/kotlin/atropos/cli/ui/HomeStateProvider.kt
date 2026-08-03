/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Health
import atropos.cli.ui.design.RunState
import atropos.core.agent.AgentQueueCheckpoint
import atropos.core.agent.AgentQueueRecord
import atropos.core.agent.AgentQueueState
import atropos.core.agent.AgentQueueStore
import atropos.core.project.ProjectRecord
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the Home cockpit's six continuous answers out of durable runtime state.
 *
 * Source Document 4 §0.1 requires the interface to answer six questions without
 * the operator searching for them. The answers are only worth rendering if they
 * are true, so every field here is either read from disk or reported as unknown.
 * Nothing is invented: a cockpit that guesses is more dangerous than one that
 * admits it cannot see. An unreadable queue is therefore distinguished from an
 * empty queue — the first is a fault, the second is a nominal idle state.
 *
 * This type owns the reading; [DashboardRenderer] owns only the drawing.
 */
class HomeStateProvider(
    private val repoRoot: Path =
        Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize(),
    private val queueStore: AgentQueueStore = AgentQueueStore(repoRoot),
    private val queueEntriesDir: Path = repoRoot.resolve(EVIDENCE_PATH).resolve("entries"),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val workspaceInspector: WorkspaceInspector = CachingGitWorkspaceInspector(),
    /** Seam: the OS ignores permission bits for root, so readability is injectable. */
    private val queueReadable: (Path) -> Boolean = { Files.isReadable(it) },
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val runtime: Runtime = Runtime.getRuntime()
) {
    private val answersBuilder = DashboardAnswersBuilder(redactionFilter, TASK_WIDTH)

    private companion object {
        const val QUEUE_WINDOW = 20
        const val TASK_WIDTH = 72
        const val EVIDENCE_PATH = ".atropos/agent/queue"
        const val BYTES_PER_MB = 1024L * 1024L
    }

    fun capture(
        activeProvider: String,
        workspace: String = repoRoot.toString()
    ): DashboardRenderer.DashboardState {
        val queue = readQueue()
        val projects = readProjects()

        val repository = try {
            workspaceInspector.inspect(workspace)
        } catch (_: Exception) {
            RepositoryState.unknown()
        }

        val active = queue?.firstOrNull {
            it.state == AgentQueueState.RUNNING || it.state == AgentQueueState.LEASED
        }
        val pending = queue?.filter { !it.state.terminal }.orEmpty()
        val failed = queue?.filter {
            it.state == AgentQueueState.FAILED ||
                it.state == AgentQueueState.REFUSED ||
                it.state == AgentQueueState.CORRUPT
        }.orEmpty()

        return DashboardRenderer.DashboardState(
            answers = DashboardRenderer.SixAnswers(
                objective = answersBuilder.objective(queue, active, pending, projects),
                doing = answersBuilder.doing(queue, active, pending),
                why = answersBuilder.why(queue, active),
                progress = answersBuilder.progress(queue, active, failed.size),
                next = answersBuilder.next(queue, active, pending, failed),
                evidence = answersBuilder.evidence(queue)
            ),
            projects = projects.orEmpty().map { project ->
                DashboardRenderer.ProjectSummary(
                    id = project.id,
                    name = project.name,
                    status = project.status.asRunState(),
                    statusLabel = project.status.canonical,
                    objective = redactionFilter.compact(project.objective, TASK_WIDTH),
                    completionIsVerifiable = project.completionIsVerifiable
                )
            },
            projectsReadable = projects != null,
            runningWork = pending.map { record ->
                DashboardRenderer.WorkItem(
                    id = record.id,
                    title = task(record),
                    state = record.state.asRunState(),
                    detail = record.checkpoint.readable(),
                    attempt = record.attempts,
                    maxAttempts = record.maxAttempts
                )
            },
            queuedItems = queue?.count { it.state == AgentQueueState.QUEUED } ?: 0,
            failedItems = failed.size,
            queueReadable = queue != null,
            provider = activeProvider,
            repository = repository,
            heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB,
            heapMaxMb = runtime.maxMemory().takeIf { it > 0 }?.div(BYTES_PER_MB) ?: 0
        )
    }

    /**
     * Returns the queue window, or `null` when the queue could not be read.
     *
     * `AgentQueueStore.listEntries` catches its own IO failures and returns an
     * empty list, which makes "I cannot read the queue" indistinguishable from
     * "there is no work" — the exact collapse §4.1 forbids, because it reports
     * a fault as a nominal idle state. The directory is therefore probed here
     * before trusting an empty result.
     *
     * A queue directory that does not exist yet is genuinely empty, not a
     * fault: nothing has ever been enqueued in this workspace.
     */
    private fun readQueue(): List<AgentQueueRecord>? {
        val exists = try {
            Files.isDirectory(queueEntriesDir)
        } catch (_: Exception) {
            return null
        }
        if (!exists) return emptyList()

        val readable = try {
            queueReadable(queueEntriesDir)
        } catch (_: Exception) {
            false
        }
        if (!readable) return null

        return try {
            queueStore.listEntries(QUEUE_WINDOW)
        } catch (_: Exception) {
            null
        }
    }

    private fun readProjects(): List<ProjectRecord>? = try {
        projectRegistry.list()
    } catch (_: Exception) {
        null
    }

    private fun ProjectStatus.asRunState(): RunState = when (this) {
        ProjectStatus.IDLE -> RunState.IDLE
        ProjectStatus.PLANNING -> RunState.QUEUED
        ProjectStatus.WAITING -> RunState.WAITING
        ProjectStatus.WORKING -> RunState.RUNNING
        ProjectStatus.REVIEW_REQUIRED -> RunState.WAITING
        ProjectStatus.BLOCKED -> RunState.BLOCKED
        ProjectStatus.COMPLETED -> RunState.COMPLETE
        ProjectStatus.FAILED -> RunState.FAILED
        ProjectStatus.CANCELLED -> RunState.CANCELLED
    }

    private fun AgentQueueState.asRunState(): RunState = when (this) {
        AgentQueueState.QUEUED -> RunState.QUEUED
        AgentQueueState.LEASED, AgentQueueState.RUNNING -> RunState.RUNNING
        AgentQueueState.RETRY_WAIT -> RunState.RETRYING
        AgentQueueState.COMPLETED -> RunState.COMPLETE
        AgentQueueState.FAILED, AgentQueueState.CORRUPT -> RunState.FAILED
        AgentQueueState.REFUSED -> RunState.BLOCKED
        AgentQueueState.CANCELLED -> RunState.CANCELLED
    }

    private fun AgentQueueCheckpoint.readable(): String =
        name.lowercase().replace('_', ' ')

}
