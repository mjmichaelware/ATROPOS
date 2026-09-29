/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-013: Deadline-Aware Scheduling
 *
 * Implements deadline-aware task scheduling for agent operations,
 * ensuring critical tasks meet their deadlines.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap

object DeadlineAwareScheduling {
    data class ScheduledTask(
        val id: String,
        val name: String,
        val deadline: Instant,
        val priority: Int = 0, // Higher = more urgent
        val estimatedDuration: Duration = Duration.ZERO,
        val callback: (() -> Unit)? = null
    ) : Comparable<ScheduledTask> {
        override fun compareTo(other: ScheduledTask): Int {
            // Earlier deadline first, then higher priority
            val deadlineCmp = this.deadline.compareTo(other.deadline)
            return if (deadlineCmp != 0) deadlineCmp else other.priority - this.priority
        }

        val isOverdue: Boolean get() = Instant.now().isAfter(deadline)
        val timeUntilDeadline: Duration get() = Duration.between(Instant.now(), deadline)
    }

    private val taskQueue = PriorityQueue<ScheduledTask>()
    private val completedTasks = ConcurrentHashMap<String, Instant>()

    /**
     * Schedules a task with a deadline.
     */
    fun schedule(
        name: String,
        deadline: Instant,
        priority: Int = 0,
        estimatedDuration: Duration = Duration.ZERO,
        callback: (() -> Unit)? = null
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        val task = ScheduledTask(id, name, deadline, priority, estimatedDuration, callback)
        taskQueue.add(task)
        return id
    }

    /**
     * Runs the scheduler loop.
     */
    fun runScheduler(): List<String> {
        val completed = mutableListOf<String>()
        val now = Instant.now()

        while (taskQueue.isNotEmpty()) {
            val task = taskQueue.peek()
            if (task == null) break

            if (task.deadline.isAfter(now)) {
                // Next task not due yet
                break
            }

            taskQueue.poll()
            task.callback?.invoke()
            completedTasks[task.id] = Instant.now()
            completed.add(task.id)
        }

        return completed
    }

    /**
     * Gets overdue tasks.
     */
    fun getOverdueTasks(): List<ScheduledTask> {
        val now = Instant.now()
        return taskQueue.filter { it.isOverdue }.toList()
    }

    /**
     * Persists schedule to CAS.
     */
    fun persistSchedule(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("deadline-schedule.json")
        val json = taskQueue.joinToString(",\n", "[\n", "\n]") { task ->
            """
                {
                    "id": "${task.id}",
                    "name": "${task.name}",
                    "deadline": "${task.deadline}",
                    "priority": ${task.priority},
                    "estimatedDurationMs": ${task.estimatedDuration.toMillis()}
                }
            """.trimIndent()
        }
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show schedule.
     */
    fun showSchedule(): String {
        return taskQueue.joinToString("\n") { "${it.name} @ ${it.deadline} (priority: ${it.priority})" }
    }
}