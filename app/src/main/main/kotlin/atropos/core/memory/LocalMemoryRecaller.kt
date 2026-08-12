package atropos.core.memory

import atropos.core.security.RedactionFilter
import java.io.File
import java.util.Locale

internal class LocalMemoryRecaller(
    private val root: File,
    private val now: () -> Long,
    private val redactionFilter: RedactionFilter,
    private val files: MemoryFileStore
) {
    fun all(limit: Int = 200): List<MemoryRecord> {
        val safeLimit = limit.coerceIn(1, 5000)
        return readSnapshot().records.takeLast(safeLimit)
    }

    fun latestByKind(kind: MemoryKind, limit: Int = 20): List<MemoryRecord> =
        all(5000)
            .asReversed()
            .filter { it.kind == kind }
            .take(limit.coerceIn(1, 200))

    fun findBySubject(subjectType: String, subjectId: String, limit: Int = 20): List<MemoryRecord> {
        val normalizedType = subjectType.trim().lowercase(Locale.US)
        val normalizedId = redactionFilter.redact(subjectId.trim())
        return all(5000)
            .asReversed()
            .filter { it.subjectType == normalizedType && it.subjectId == normalizedId }
            .take(limit.coerceIn(1, 200))
    }

    fun findBySubjectTypes(subjectTypes: Set<String>, limit: Int = 20): List<MemoryRecord> {
        val normalizedTypes = subjectTypes
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .toSet()
        if (normalizedTypes.isEmpty()) return emptyList()
        return readSnapshot().records
            .asReversed()
            .filter { record -> record.subjectType in normalizedTypes }
            .take(limit.coerceIn(1, 200))
    }

    fun search(query: String, limit: Int = 20): List<MemorySearchHit> {
        val terms = query.lowercase(Locale.US)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        if (terms.isEmpty()) return emptyList()

        val hits = mutableListOf<MemorySearchHit>()
        val records = all(5000)
        for (record in records) {
            val haystack = buildString {
                append(record.title)
                append('\n')
                append(record.body)
                append('\n')
                append(record.tags.joinToString(" "))
                append('\n')
                append(record.subjectType.orEmpty())
                append('\n')
                append(record.subjectId.orEmpty())
            }.lowercase(Locale.US)

            var score = 0
            for (term in terms) {
                if (record.title.lowercase(Locale.US).contains(term)) score += 5
                if (record.tags.any { it.contains(term) }) score += 4
                if (record.body.lowercase(Locale.US).contains(term)) score += 2
                if (haystack.contains(term)) score += 1
            }
            if (score > 0) hits += MemorySearchHit(record, score)
        }

        return hits.sortedWith(
            compareByDescending<MemorySearchHit> { it.score }
                .thenByDescending { it.record.createdAtEpochMs }
        ).take(limit.coerceIn(1, 100))
    }

    private fun readSnapshot(): MemorySnapshot {
        val prior = files.readState()
        if (!files.recordsExist()) {
            return MemorySnapshot(emptyList(), prior?.corruptRecords ?: 0, prior?.compactedAtEpochMs)
        }
        val read = files.readRecords()
        return MemorySnapshot(
            read.records,
            maxOf(read.corruptRecords, prior?.corruptRecords ?: 0),
            prior?.compactedAtEpochMs
        )
    }

    private data class MemorySnapshot(
        val records: List<MemoryRecord>,
        val corruptRecords: Int,
        val compactedAtEpochMs: Long?
    )
}
