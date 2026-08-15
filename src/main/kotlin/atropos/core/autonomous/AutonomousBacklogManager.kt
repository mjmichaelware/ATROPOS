/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.autonomous

import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe, restart-safe manager for the Autonomous Backlog.
 * Wraps [AutonomousBacklogService] with synchronised transaction boundaries
 * to prevent race conditions during concurrent execution.
 */
class AutonomousBacklogManager(
    private val service: AutonomousBacklogService
) {
    private val lock = ReentrantLock()

    fun enqueueTask(
        kind: AutonomousTaskKind,
        description: String,
        priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM,
        context: Map<String, String> = emptyMap(),
        dependencies: List<String> = emptyList()
    ): AutonomousTask = lock.withLock {
        service.enqueue(kind, description, priority, context, dependencies)
    }

    fun getNextEligibleTask(): AutonomousTask? = lock.withLock {
        service.eligible().firstOrNull()
    }

    fun claimTask(taskId: String): AutonomousTask? = lock.withLock {
        service.claim(taskId)
    }

    fun completeTask(taskId: String, result: String? = null) = lock.withLock {
        service.complete(taskId, result)
    }

    fun failTask(taskId: String, error: String) = lock.withLock {
        service.fail(taskId, error)
    }

    fun skipTask(taskId: String, reason: String) = lock.withLock {
        service.skip(taskId, reason)
    }

    fun getTaskDetails(taskId: String): AutonomousTask? = lock.withLock {
        service.getTask(taskId)
    }

    fun getBacklogSnapshot(): AutonomousBacklog = lock.withLock {
        service.snapshot()
    }

    fun clearAllTasks(backlogDir: Path) = lock.withLock {
        val taskFile = backlogDir.resolve(".atropos/autonomous/tasks.jsonl")
        java.nio.file.Files.deleteIfExists(taskFile)
    }
}
