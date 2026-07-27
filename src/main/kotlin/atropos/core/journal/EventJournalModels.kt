package atropos.core.journal

import java.time.Instant

enum class EventCategory {
    LIFECYCLE,
    STATUS,
    TEXT,
    REASONING,
    TOOL_CALL,
    COMMAND,
    STDOUT,
    STDERR,
    FILE_READ,
    FILE_MUTATION,
    DIFF,
    TEST,
    VERIFICATION,
    TODO,
    HEARTBEAT,
    WARNING,
    ERROR,
    CONTINUATION,
    CHILD_RUN,
    COMPLETION,
    FAILURE,
    CANCELLATION,
    POLICY,
    DAG,
    QUEUE
}

data class EventJournalRecord(
    val sequence: Long,
    val timestamp: Instant,
    val category: EventCategory,
    val payload: String,
    val goalId: String? = null,
    val projectId: String? = null,
    val dagId: String? = null,
    val atomId: String? = null,
    val jobId: String? = null,
    val attemptId: String? = null,
    val runId: String? = null,
    val parentRunId: String? = null,
    val providerId: String? = null,
    val providerSessionId: String? = null
) {
    fun render(): String = buildString {
        append("#$sequence ")
        append(timestamp.toString().substringAfter("T").substringBefore("."))
        append(" [$category]")
        goalId?.let { append(" goal=$it") }
        dagId?.let { append(" dag=$it") }
        jobId?.let { append(" job=$it") }
        runId?.let { append(" run=$it") }
        providerId?.let { append(" provider=$it") }
        append(": ")
        append(payload.take(200))
    }

    fun toJournalLine(): String = buildString {
        append(sequence)
        append('\t').append(timestamp)
        append('\t').append(category.name)
        append('\t').append(encodeTab(payload))
        goalId?.let { append('\t').append("goalId=$it") }
        projectId?.let { append('\t').append("projectId=$it") }
        dagId?.let { append('\t').append("dagId=$it") }
        atomId?.let { append('\t').append("atomId=$it") }
        jobId?.let { append('\t').append("jobId=$it") }
        attemptId?.let { append('\t').append("attemptId=$it") }
        runId?.let { append('\t').append("runId=$it") }
        parentRunId?.let { append('\t').append("parentRunId=$it") }
        providerId?.let { append('\t').append("providerId=$it") }
        providerSessionId?.let { append('\t').append("providerSessionId=$it") }
    }

    companion object {
        fun fromJournalLine(line: String): EventJournalRecord? {
            val parts = line.split('\t')
            if (parts.size < 4) return null
            val sequence = parts[0].toLongOrNull() ?: return null
            val timestamp = runCatching { Instant.parse(parts[1]) }.getOrNull() ?: return null
            val category = runCatching { EventCategory.valueOf(parts[2]) }.getOrNull() ?: return null
            val payload = decodeTab(parts[3])
            var goalId: String? = null
            var projectId: String? = null
            var dagId: String? = null
            var atomId: String? = null
            var jobId: String? = null
            var attemptId: String? = null
            var runId: String? = null
            var parentRunId: String? = null
            var providerId: String? = null
            var providerSessionId: String? = null
            for (i in 4 until parts.size) {
                val kv = parts[i].split('=', limit = 2)
                if (kv.size != 2) continue
                when (kv[0]) {
                    "goalId" -> goalId = kv[1].takeIf { it.isNotBlank() }
                    "projectId" -> projectId = kv[1].takeIf { it.isNotBlank() }
                    "dagId" -> dagId = kv[1].takeIf { it.isNotBlank() }
                    "atomId" -> atomId = kv[1].takeIf { it.isNotBlank() }
                    "jobId" -> jobId = kv[1].takeIf { it.isNotBlank() }
                    "attemptId" -> attemptId = kv[1].takeIf { it.isNotBlank() }
                    "runId" -> runId = kv[1].takeIf { it.isNotBlank() }
                    "parentRunId" -> parentRunId = kv[1].takeIf { it.isNotBlank() }
                    "providerId" -> providerId = kv[1].takeIf { it.isNotBlank() }
                    "providerSessionId" -> providerSessionId = kv[1].takeIf { it.isNotBlank() }
                }
            }
            return EventJournalRecord(
                sequence = sequence, timestamp = timestamp, category = category,
                payload = payload, goalId = goalId, projectId = projectId,
                dagId = dagId, atomId = atomId, jobId = jobId,
                attemptId = attemptId, runId = runId, parentRunId = parentRunId,
                providerId = providerId, providerSessionId = providerSessionId
            )
        }

        private fun encodeTab(value: String): String = value.replace("\t", "\\t").replace("\n", "\\n")
        private fun decodeTab(value: String): String = value.replace("\\t", "\t").replace("\\n", "\n")
    }
}

data class EventJournalSummary(
    val runId: String,
    val eventCount: Long,
    val categories: Map<EventCategory, Int>,
    val firstEvent: Instant?,
    val lastEvent: Instant?
)
