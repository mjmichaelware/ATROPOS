package atropos.core.autonomous

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

class AutonomousBacklogService(
    private val root: Path = Path.of(System.getProperty("user.dir"))
) {
    private val backlogDir = root.resolve(".atropos/autonomous")
    private val taskFile = backlogDir.resolve("tasks.jsonl")
    private val repairFile = backlogDir.resolve("repairs.jsonl")
    private val failoverFile = backlogDir.resolve("failovers.jsonl")

    private val tasks = mutableListOf<AutonomousTask>()
    private val repairs = mutableListOf<RepairRecord>()
    private val failovers = mutableListOf<ProviderFailoverEvent>()

    init {
        tasks.addAll(loadTasks())
        repairs.addAll(loadRepairs())
        failovers.addAll(loadFailovers())
    }

    fun enqueue(kind: AutonomousTaskKind, description: String, priority: AutonomousTaskPriority = AutonomousTaskPriority.MEDIUM, context: Map<String, String> = emptyMap(), dependencies: List<String> = emptyList()): AutonomousTask {
        val task = AutonomousTask(
            kind = kind,
            priority = priority,
            description = description,
            context = context,
            dependencies = dependencies,
            state = if (dependencies.isEmpty()) AutonomousTaskState.ELIGIBLE else AutonomousTaskState.PENDING
        )
        tasks += task
        persistTasks()
        return task
    }

    fun eligible(): List<AutonomousTask> {
        return tasks.filter { task ->
            task.state == AutonomousTaskState.ELIGIBLE &&
            task.dependencies.all { depId ->
                tasks.firstOrNull { it.id == depId }?.state == AutonomousTaskState.COMPLETED
            }
        }.sortedBy { it.priority.ordinal }
    }

    fun claim(taskId: String): AutonomousTask? {
        val idx = tasks.indexOfFirst { it.id == taskId && it.state == AutonomousTaskState.ELIGIBLE }
        if (idx < 0) return null
        tasks[idx] = tasks[idx].copy(state = AutonomousTaskState.RUNNING, startedAt = Instant.now())
        persistTasks()
        return tasks[idx]
    }

    fun complete(taskId: String, result: String? = null) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            tasks[idx] = tasks[idx].copy(state = AutonomousTaskState.COMPLETED, completedAt = Instant.now(), result = result)
            unblockDependents(taskId)
            persistTasks()
        }
    }

    fun fail(taskId: String, error: String) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return
        val task = tasks[idx]
        if (task.retryCount < task.maxRetries) {
            tasks[idx] = task.copy(state = AutonomousTaskState.ELIGIBLE, retryCount = task.retryCount + 1, result = "retry #${task.retryCount + 1}: $error")
        } else {
            tasks[idx] = task.copy(state = AutonomousTaskState.FAILED, completedAt = Instant.now(), result = "failed after ${task.maxRetries} retries: $error")
        }
        persistTasks()
    }

    fun skip(taskId: String, reason: String) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            tasks[idx] = tasks[idx].copy(
                state = AutonomousTaskState.SKIPPED,
                completedAt = Instant.now(),
                result = reason
            )
            persistTasks()
        }
    }

    fun markEligible(taskId: String) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0 && tasks[idx].state == AutonomousTaskState.PENDING) {
            tasks[idx] = tasks[idx].copy(state = AutonomousTaskState.ELIGIBLE)
            persistTasks()
        }
    }

    fun getTask(id: String): AutonomousTask? = tasks.firstOrNull { it.id == id }

    fun snapshot(): AutonomousBacklog = AutonomousBacklog(tasks = tasks.toList())

    fun recordRepair(taskId: String, failureSignature: String, repairAction: String, success: Boolean, durationMs: Long): RepairRecord {
        val record = RepairRecord(
            taskId = taskId,
            failureSignature = failureSignature,
            repairAction = repairAction,
            success = success,
            attemptNumber = repairs.count { it.taskId == taskId && it.failureSignature == failureSignature } + 1,
            durationMs = durationMs
        )
        repairs += record
        persistRepairs()
        return record
    }

    fun repairHistory(): List<RepairRecord> = repairs.toList()
    fun findRepairs(signature: String): List<RepairRecord> = repairs.filter { it.failureSignature == signature }

    fun recordFailover(primary: String, fallback: String, reason: String, success: Boolean, escalated: Boolean = false): ProviderFailoverEvent {
        val event = ProviderFailoverEvent(
            primaryProviderId = primary,
            fallbackProviderId = fallback,
            failureReason = reason,
            success = success,
            escalated = escalated
        )
        failovers += event
        persistFailovers()
        return event
    }

    fun failoverHistory(): List<ProviderFailoverEvent> = failovers.toList()

    private fun unblockDependents(completedTaskId: String) {
        tasks.forEachIndexed { idx, task ->
            if (task.state == AutonomousTaskState.PENDING && completedTaskId in task.dependencies) {
                val allDepsDone = task.dependencies.all { depId ->
                    tasks.firstOrNull { it.id == depId }?.state == AutonomousTaskState.COMPLETED
                }
                if (allDepsDone) {
                    tasks[idx] = task.copy(state = AutonomousTaskState.ELIGIBLE)
                }
            }
        }
    }

    private fun loadTasks(): List<AutonomousTask> = readLines(taskFile).mapNotNull { lineToTask(it) }
    private fun persistTasks() = writeLines(taskFile, tasks.map { taskToLine(it) })
    private fun loadRepairs(): List<RepairRecord> = readLines(repairFile).mapNotNull { lineToRepair(it) }
    private fun persistRepairs() = writeLines(repairFile, repairs.map { repairToLine(it) })
    private fun loadFailovers(): List<ProviderFailoverEvent> = readLines(failoverFile).mapNotNull { lineToFailover(it) }
    private fun persistFailovers() = writeLines(failoverFile, failovers.map { failoverToLine(it) })

    private fun taskToLine(t: AutonomousTask): String {
        val ctx = t.context.entries.joinToString("&") { "${it.key}=${it.value}" }
        return listOf(t.id, t.kind.name, t.priority.name, t.state.name, t.description.replace('\n', ' '),
            ctx, t.dependencies.joinToString(","), t.retryCount.toString(), t.maxRetries.toString(),
            t.createdAt.toString(), t.startedAt?.toString() ?: "", t.completedAt?.toString() ?: "", t.result ?: "")
            .joinToString("\t")
    }

    private fun lineToTask(line: String): AutonomousTask? {
        val parts = line.split("\t"); if (parts.size < 10) return null
        return try { AutonomousTask(id = parts[0], kind = AutonomousTaskKind.valueOf(parts[1]),
            priority = AutonomousTaskPriority.valueOf(parts[2]), state = AutonomousTaskState.valueOf(parts[3]),
            description = parts[4], context = parseCtx(parts[5]), dependencies = parts[6].split(",").filter { it.isNotBlank() },
            retryCount = parts[7].toInt(), maxRetries = parts[8].toInt(), createdAt = Instant.parse(parts[9]),
            startedAt = if (parts[10].isNotBlank()) Instant.parse(parts[10]) else null,
            completedAt = if (parts[11].isNotBlank()) Instant.parse(parts[11]) else null, result = parts[12].ifBlank { null }) }
        catch (_: Exception) { null }
    }

    private fun repairToLine(r: RepairRecord): String =
        listOf(r.id, r.taskId, r.failureSignature.replace('\n', ' '), r.repairAction.replace('\n', ' '),
            r.success.toString(), r.attemptNumber.toString(), r.durationMs.toString(), r.createdAt.toString()).joinToString("\t")

    private fun lineToRepair(line: String): RepairRecord? {
        val parts = line.split("\t"); if (parts.size < 8) return null
        return try { RepairRecord(id = parts[0], taskId = parts[1], failureSignature = parts[2],
            repairAction = parts[3], success = parts[4].toBoolean(), attemptNumber = parts[5].toInt(),
            durationMs = parts[6].toLong(), createdAt = Instant.parse(parts[7])) }
        catch (_: Exception) { null }
    }

    private fun failoverToLine(f: ProviderFailoverEvent): String =
        listOf(f.id, f.primaryProviderId, f.fallbackProviderId, f.failureReason.replace('\n', ' '),
            f.durationMs.toString(), f.success.toString(), f.escalated.toString(), f.timestamp.toString()).joinToString("\t")

    private fun lineToFailover(line: String): ProviderFailoverEvent? {
        val parts = line.split("\t"); if (parts.size < 8) return null
        return try { ProviderFailoverEvent(id = parts[0], primaryProviderId = parts[1],
            fallbackProviderId = parts[2], failureReason = parts[3], durationMs = parts[4].toLong(),
            success = parts[5].toBoolean(), escalated = parts[6].toBoolean(), timestamp = Instant.parse(parts[7])) }
        catch (_: Exception) { null }
    }

    private fun parseCtx(raw: String): Map<String, String> =
        raw.split("&").mapNotNull { kv -> val eq = kv.indexOf('='); if (eq < 0) null else kv.substring(0, eq) to kv.substring(eq + 1) }.toMap()

    private fun readLines(path: Path): List<String> {
        if (!Files.isRegularFile(path)) return emptyList()
        return Files.readAllLines(path, StandardCharsets.UTF_8).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun writeLines(path: Path, lines: List<String>) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, lines.joinToString("\n") + "\n", StandardCharsets.UTF_8)
        try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING) }
    }
}
