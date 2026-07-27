package atropos.core.journal

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class EventJournalService(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val runsRoot = repoRoot.resolve(".atropos/runs").normalize()

    fun record(
        runId: String,
        category: EventCategory,
        payload: String,
        goalId: String? = null,
        projectId: String? = null,
        dagId: String? = null,
        atomId: String? = null,
        jobId: String? = null,
        attemptId: String? = null,
        parentRunId: String? = null,
        providerId: String? = null,
        providerSessionId: String? = null
    ): EventJournalRecord {
        val runDir = runsRoot.resolve(runId)
        Files.createDirectories(runDir)
        val journalFile = runDir.resolve("events.journal")

        val sequence = nextSequence(journalFile)
        val now = clock()
        val record = EventJournalRecord(
            sequence = sequence,
            timestamp = now,
            category = category,
            payload = redactionFilter.compact(payload, 4000),
            goalId = goalId,
            projectId = projectId,
            dagId = dagId,
            atomId = atomId,
            jobId = jobId,
            attemptId = attemptId,
            runId = runId,
            parentRunId = parentRunId,
            providerId = providerId,
            providerSessionId = providerSessionId
        )

        // Append-only write
        val line = record.toJournalLine() + "\n"
        Files.writeString(
            journalFile,
            line,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )

        return record
    }

    fun readEvents(runId: String, limit: Int = 200, offset: Long = 0): List<EventJournalRecord> {
        val journalFile = runsRoot.resolve(runId).resolve("events.journal")
        if (!Files.isRegularFile(journalFile)) return emptyList()

        return Files.readAllLines(journalFile, StandardCharsets.UTF_8)
            .mapNotNull { EventJournalRecord.fromJournalLine(it) }
            .filter { it.sequence > offset }
            .takeLast(limit.coerceAtLeast(1))
    }

    fun readEventsByCategory(runId: String, category: EventCategory, limit: Int = 100): List<EventJournalRecord> {
        return readEvents(runId, 5000).filter { it.category == category }.takeLast(limit.coerceAtLeast(1))
    }

    fun summary(runId: String): EventJournalSummary? {
        val journalFile = runsRoot.resolve(runId).resolve("events.journal")
        if (!Files.isRegularFile(journalFile)) return null
        val events = readEvents(runId, 5000)
        if (events.isEmpty()) return null
        return EventJournalSummary(
            runId = runId,
            eventCount = events.size.toLong(),
            categories = events.groupBy { it.category }.mapValues { it.value.size },
            firstEvent = events.firstOrNull()?.timestamp,
            lastEvent = events.lastOrNull()?.timestamp
        )
    }

    fun listRunIds(): List<String> {
        if (!Files.isDirectory(runsRoot)) return emptyList()
        return Files.list(runsRoot).use { stream ->
            stream
                .filter { Files.isDirectory(it) }
                .filter { Files.isRegularFile(it.resolve("events.journal")) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    fun latestRunId(): String? {
        return latestRunIds(1).firstOrNull()
    }

    fun latestRunIds(limit: Int = 20): List<String> {
        val runIds = listRunIds()
        if (runIds.isEmpty()) return emptyList()
        return runIds.sortedWith(
            compareBy<String> { summary(it)?.lastEvent ?: Instant.EPOCH }
                .thenBy { it }
        ).takeLast(limit.coerceAtLeast(1)).asReversed()
    }

    fun transcript(runId: String, limit: Int = 100): String = buildString {
        val events = readEvents(runId, limit)
        if (events.isEmpty()) {
            appendLine("no events for run: $runId")
            return@buildString
        }
        events.forEach { event ->
            appendLine(event.render())
        }
    }.trimEnd()

    fun diffEvents(runId: String, limit: Int = 50): List<EventJournalRecord> =
        readEventsByCategory(runId, EventCategory.DIFF, limit)

    fun testEvents(runId: String, limit: Int = 50): List<EventJournalRecord> =
        readEventsByCategory(runId, EventCategory.TEST, limit)

    fun commandEvents(runId: String, limit: Int = 50): List<EventJournalRecord> =
        readEventsByCategory(runId, EventCategory.COMMAND, limit)

    private fun nextSequence(journalFile: Path): Long {
        if (!Files.isRegularFile(journalFile)) return 1L
        val lastSequence = Files.readAllLines(journalFile, StandardCharsets.UTF_8)
            .asReversed()
            .firstNotNullOfOrNull { line ->
                EventJournalRecord.fromJournalLine(line)?.sequence
            }
            ?: 0L
        return lastSequence + 1L
    }
}
