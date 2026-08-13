package atropos.core.execution

import atropos.core.json.JsonStringField

import atropos.core.policy.BoundedProcessRunner
import java.io.File
import java.nio.file.Path
import java.util.Locale

enum class WorkStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}

data class WorkItem(
    val id: String,
    val label: String,
    val command: List<String>,
    val status: WorkStatus,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val attempts: Int,
    val lastExitCode: Int?,
    val lastOutputTail: String
)

data class WorkRunResult(
    val item: WorkItem,
    val exitCode: Int,
    val outputTail: String
)

data class WorkQueueStatus(
    val root: File,
    val total: Int,
    val queued: Int,
    val running: Int,
    val succeeded: Int,
    val failed: Int,
    val githubActionsConfigured: Boolean,
    val cloudflareConfigured: Boolean,
    val supabaseEdgeConfigured: Boolean,
    val googleCloudConfigured: Boolean
)

class LocalWorkQueue(
    private val root: File = File(".atropos/work-queue"),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val env: Map<String, String> = System.getenv()
) {
    private val processRunner = BoundedProcessRunner()
    private val queueFile = File(root, "queue.jsonl")
    private val activeCommands = mutableMapOf<String, List<String>>()

    fun enqueue(label: String, command: List<String>): WorkItem {
        require(command.isNotEmpty()) { "command must not be empty" }
        root.mkdirs()
        val createdAt = now()
        val id = "job-${createdAt}-${safeSuffix(label)}"
        activeCommands[id] = command.toList()
        val item = WorkItem(
            id = id,
            label = label.trim().ifEmpty { "local-work" },
            command = redactCommand(command),
            status = WorkStatus.QUEUED,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = createdAt,
            attempts = 0,
            lastExitCode = null,
            lastOutputTail = ""
        )
        append(item)
        return item
    }

    fun enqueueLocalCompile(): WorkItem {
        return enqueue(
            label = "local compile",
            command = listOf(
                "sh",
                "-c",
                "find src/main/kotlin -type f -name '*.kt' > .atropos/sources.txt && kotlinc -d .atropos/classes @.atropos/sources.txt"
            )
        )
    }

    fun all(limit: Int = 200): List<WorkItem> {
        val safeLimit = limit.coerceIn(1, 1000)
        if (!queueFile.exists()) return emptyList()
        val decoded = queueFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { WorkItemCodec.decode(it) }
        return decoded.takeLast(safeLimit)
    }

    fun status(): WorkQueueStatus {
        val items = all(1000)
        return WorkQueueStatus(
            root = root,
            total = items.size,
            queued = items.count { it.status == WorkStatus.QUEUED },
            running = items.count { it.status == WorkStatus.RUNNING },
            succeeded = items.count { it.status == WorkStatus.SUCCEEDED },
            failed = items.count { it.status == WorkStatus.FAILED },
            githubActionsConfigured = env["GITHUB_TOKEN"].isNullOrBlank().not(),
            cloudflareConfigured = env["CLOUDFLARE_API_TOKEN"].isNullOrBlank().not() &&
                env["CLOUDFLARE_ACCOUNT_ID"].isNullOrBlank().not(),
            supabaseEdgeConfigured = env["SUPABASE_URL"].isNullOrBlank().not() &&
                env["SUPABASE_ANON_KEY"].isNullOrBlank().not(),
            googleCloudConfigured = env["GOOGLE_APPLICATION_CREDENTIALS"].isNullOrBlank().not()
        )
    }

    fun runNext(timeoutMs: Long = 30_000L): WorkRunResult? {
        val items = all(1000)
        val next = items.firstOrNull { it.status == WorkStatus.QUEUED } ?: return null
        val running = next.copy(status = WorkStatus.RUNNING, updatedAtEpochMs = now(), attempts = next.attempts + 1)
        rewrite(items.replace(next.id, running))

        val command = activeCommands.remove(running.id)
        val result = if (command == null) {
            125 to "command unavailable after queue reload"
        } else {
            runCommand(command, timeoutMs)
        }
        val finished = running.copy(
            status = if (result.first == 0) WorkStatus.SUCCEEDED else WorkStatus.FAILED,
            updatedAtEpochMs = now(),
            lastExitCode = result.first,
            lastOutputTail = redactOutput(result.second)
        )
        rewrite(all(1000).replace(running.id, finished))
        return WorkRunResult(finished, result.first, finished.lastOutputTail)
    }

    private fun runCommand(command: List<String>, timeoutMs: Long): Pair<Int, String> {
        val result = runCatching {
            processRunner.run(
                command = command,
                directory = Path.of("."),
                timeoutMillis = timeoutMs,
                maxOutputBytes = 8_000,
                maxOutputLines = 1_000
            )
        }.getOrElse { failure ->
            return 125 to tail(failure.javaClass.simpleName + ": " + (failure.message ?: "command failed"))
        }
        val output = result.stdout + result.stderr
        if (result.timedOut) return 124 to tail(output + "\ntimeout")
        if (result.launchError != null) return 125 to tail(result.launchError)
        return (result.exitCode ?: 125) to tail(output)
    }

    private fun append(item: WorkItem) {
        root.mkdirs()
        queueFile.appendText(WorkItemCodec.encode(item) + "\n")
    }

    private fun rewrite(items: List<WorkItem>) {
        root.mkdirs()
        queueFile.writeText(items.joinToString("\n") { WorkItemCodec.encode(it) } + if (items.isEmpty()) "" else "\n")
    }

    private fun List<WorkItem>.replace(id: String, item: WorkItem): List<WorkItem> {
        return map { if (it.id == id) item else it }
    }

    private fun safeSuffix(label: String): String {
        val cleaned = label.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
        return cleaned.ifEmpty { "work" }
    }

    private fun tail(value: String, max: Int = 2000): String {
        return if (value.length <= max) value else value.takeLast(max)
    }

    private fun redactCommand(command: List<String>): List<String> {
        return command.map { "[REDACTED]" }
    }

    private fun redactOutput(output: String): String {
        return if (output.isEmpty()) "" else "[REDACTED]"
    }
}

object WorkItemCodec {
    fun encode(item: WorkItem): String {
        return buildString {
            append("{")
            append("\"id\":\"").append(escape(item.id)).append("\",")
            append("\"label\":\"").append(escape(item.label)).append("\",")
            append("\"command\":[")
            item.command.forEachIndexed { index, _ ->
                if (index > 0) append(",")
                append("\"").append("[REDACTED]").append("\"")
            }
            append("],")
            append("\"status\":\"").append(item.status.name).append("\",")
            append("\"createdAtEpochMs\":").append(item.createdAtEpochMs).append(",")
            append("\"updatedAtEpochMs\":").append(item.updatedAtEpochMs).append(",")
            append("\"attempts\":").append(item.attempts).append(",")
            append("\"lastExitCode\":").append(item.lastExitCode ?: -999999).append(",")
            append("\"lastOutputTail\":\"").append(escape(redactOutput(item.lastOutputTail))).append("\"")
            append("}")
        }
    }

    private fun redactOutput(output: String): String {
        return if (output.isEmpty()) "" else "[REDACTED]"
    }

    fun decode(line: String): WorkItem? {
        return try {
            WorkItem(
                id = stringField(line, "id") ?: return null,
                label = stringField(line, "label") ?: "",
                command = commandField(line),
                status = WorkStatus.valueOf(stringField(line, "status") ?: return null),
                createdAtEpochMs = longField(line, "createdAtEpochMs") ?: 0L,
                updatedAtEpochMs = longField(line, "updatedAtEpochMs") ?: 0L,
                attempts = intField(line, "attempts") ?: 0,
                lastExitCode = intField(line, "lastExitCode")?.let { if (it == -999999) null else it },
                lastOutputTail = stringField(line, "lastOutputTail") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun stringField(json: String, name: String): String? {
        return JsonStringField.value(json, name)?.let { unescape(it) }
    }

    private fun longField(json: String, name: String): Long? {
        val regex = Regex(""""$name"\s*:\s*([0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun intField(json: String, name: String): Int? {
        val regex = Regex(""""$name"\s*:\s*(-?[0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun commandField(json: String): List<String> {
        // Bracket depth is tracked outside strings, so an argument containing
        // "]" no longer truncates the command.
        val raw = JsonStringField.arrayBody(json, "command") ?: return emptyList()
        return JsonStringField.values(raw).map(::unescape)
    }

    private fun escape(value: String): String {
        return buildString {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }
}
