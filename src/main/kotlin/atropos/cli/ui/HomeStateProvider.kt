/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Health
import atropos.cli.ui.design.RunState
import atropos.core.agent.AgentQueueCheckpoint
import atropos.core.agent.AgentQueueRecord
import atropos.core.agent.AgentQueueState
import atropos.core.agent.AgentQueueStore
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
    private val workspaceInspector: WorkspaceInspector = CachingGitWorkspaceInspector(),
    /** Seam: the OS ignores permission bits for root, so readability is injectable. */
    private val queueReadable: (Path) -> Boolean = { Files.isReadable(it) },
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val runtime: Runtime = Runtime.getRuntime()
) {
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
                objective = objective(queue, active, pending),
                doing = doing(queue, active, pending),
                why = why(queue, active),
                progress = progress(queue, active, failed.size),
                next = next(queue, active, pending, failed),
                evidence = evidence(queue)
            ),
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

    // ---- the six answers ----------------------------------------------------

    /** 1. What am I trying to accomplish? */
    private fun objective(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?,
        pending: List<AgentQueueRecord>
    ): DashboardRenderer.Answer = when {
        queue == null -> unreadable()
        active != null -> DashboardRenderer.Answer(task(active), Health.VERIFIED)
        pending.isNotEmpty() -> DashboardRenderer.Answer(task(pending.first()), Health.PENDING)
        else -> DashboardRenderer.Answer("no objective queued", Health.UNKNOWN)
    }

    /** 2. What is ATROPOS doing? */
    private fun doing(
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

    /**
     * 3. Why is it doing that?
     *
     * Recorded rationale only. When an entry carries no source evidence the
     * honest answer is that none was recorded — not a plausible-sounding
     * reconstruction of intent.
     */
    private fun why(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?
    ): DashboardRenderer.Answer = when {
        queue == null -> unreadable()
        active == null -> DashboardRenderer.Answer("nothing running", Health.UNKNOWN)
        !active.sourceEvidence.isNullOrBlank() -> DashboardRenderer.Answer(
            redactionFilter.compact(active.sourceEvidence!!, TASK_WIDTH),
            Health.VERIFIED
        )
        else -> DashboardRenderer.Answer(
            "no rationale recorded · provider ${active.provider ?: "unassigned"}",
            Health.PENDING
        )
    }

    /** 4. How far along is it? */
    private fun progress(
        queue: List<AgentQueueRecord>?,
        active: AgentQueueRecord?,
        failed: Int
    ): DashboardRenderer.Answer {
        if (queue == null) return unreadable()
        if (queue.isEmpty()) return DashboardRenderer.Answer("nothing tracked", Health.UNKNOWN)

        val complete = queue.count { it.state == AgentQueueState.COMPLETED }
        val checkpoint = active?.let { " · ${it.checkpoint.readable()}" }.orEmpty()
        val health = when {
            failed > 0 -> Health.ERROR
            complete == queue.size -> Health.VERIFIED
            else -> Health.PENDING
        }
        return DashboardRenderer.Answer("$complete/${queue.size} complete$checkpoint", health)
    }

    /**
     * 5. What should I do next?
     *
     * Always a command the operator can actually type. "What should I do next?"
     * is not answered by a description of the situation.
     */
    private fun next(
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

    /** 6. Can I inspect the evidence? */
    private fun evidence(queue: List<AgentQueueRecord>?): DashboardRenderer.Answer {
        if (queue == null) return unreadable()

        val linked = queue.count {
            !it.verificationId.isNullOrBlank() || !it.sourceEvidence.isNullOrBlank()
        }
        return if (linked > 0) {
            DashboardRenderer.Answer("$linked linked · $EVIDENCE_PATH", Health.VERIFIED)
        } else {
            DashboardRenderer.Answer("none recorded · $EVIDENCE_PATH", Health.UNKNOWN)
        }
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Translates the runtime's queue enum into the Section A status vocabulary
     * (§3.3: "status names describe user progress instead of internal
     * implementation whenever possible"). `LEASED` and `RETRY_WAIT` are runtime
     * mechanics; the operator sees working and retrying.
     */
    private fun AgentQueueState.asRunState(): RunState = when (this) {
        AgentQueueState.QUEUED -> RunState.QUEUED
        AgentQueueState.LEASED, AgentQueueState.RUNNING -> RunState.RUNNING
        AgentQueueState.RETRY_WAIT -> RunState.RETRYING
        AgentQueueState.COMPLETED -> RunState.COMPLETE
        AgentQueueState.FAILED, AgentQueueState.CORRUPT -> RunState.FAILED
        AgentQueueState.REFUSED -> RunState.BLOCKED
        AgentQueueState.CANCELLED -> RunState.CANCELLED
    }

    /** `PATCH_APPLIED` is an implementation token; the operator reads prose. */
    private fun AgentQueueCheckpoint.readable(): String =
        name.lowercase().replace('_', ' ')

    private fun unreadable(): DashboardRenderer.Answer =
        DashboardRenderer.Answer("unreadable · $EVIDENCE_PATH", Health.ERROR)

    /** Queue tasks are operator text and may carry secrets; §13 forbids leaking them. */
    private fun task(record: AgentQueueRecord): String =
        redactionFilter.compact(redactionFilter.redact(record.task), TASK_WIDTH)

}
