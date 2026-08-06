package atropos.core.memory

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class LocalMemoryStore(
    private val root: File = defaultRoot(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val env: Map<String, String> = System.getenv(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val jsonlFile = File(root, "memory.jsonl")
    private val stateFile = File(root, "memory.state")
    private val files = MemoryFileStore(root, jsonlFile, stateFile, now)
    private val backends = MemoryBackendProbe()
    private val sourceChunker = MemorySourceChunker()
    private val vectorIndex = SqliteVecMemoryIndex(File(root, "source-vectors.db"))

    companion object {
        fun defaultRoot(): File = AtroposRepoRootLocator.resolve().resolve(".atropos/memory").toFile()
    }

    fun remember(
        kind: MemoryKind,
        title: String,
        body: String,
        tags: List<String> = emptyList()
    ): MemoryRecord = rememberDetailed(kind, title, body, tags)

    fun rememberDetailed(
        kind: MemoryKind,
        title: String,
        body: String,
        tags: List<String> = emptyList(),
        subjectType: String? = null,
        subjectId: String? = null,
        sourceCoordinate: String? = null,
        authority: MemoryAuthority = MemoryAuthority.OBSERVATION
    ): MemoryRecord {
        root.mkdirs()
        val cleanedTitle = cleanedTitle(kind, title)
        val cleanedBody = redactionFilter.redact(body.trim())
        val cleanedTags = normalizeTags(tags)
        val cleanedSubjectType = subjectType?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
        val cleanedSubjectId = subjectId?.trim()?.takeIf { it.isNotBlank() }?.let(redactionFilter::redact)
        val cleanedSourceCoordinate = sourceCoordinate?.trim()?.takeIf { it.isNotBlank() }?.let(redactionFilter::redact)
        val createdAt = now()
        val id = stableId(kind, cleanedTitle, cleanedBody, createdAt, cleanedSubjectType, cleanedSubjectId)
        val unsignedRecord = MemoryRecord(
            id = id,
            kind = kind,
            title = cleanedTitle,
            body = cleanedBody,
            tags = cleanedTags,
            createdAtEpochMs = createdAt,
            subjectType = cleanedSubjectType,
            subjectId = cleanedSubjectId,
            contentSha256 = "",
            failureSignature = if (kind == MemoryKind.FAILURE) stableFingerprint(
                listOf(cleanedTitle, cleanedBody, cleanedSubjectType.orEmpty()).joinToString("|")
            ) else null,
            sourceCoordinate = cleanedSourceCoordinate,
            authority = authority
        )
        val record = unsignedRecord.copy(contentSha256 = MemoryRecordCodec.recordSha256(unsignedRecord))
        val priorState = files.readState()
        val priorSnapshot = if (priorState == null && jsonlFile.exists()) readSnapshot() else null
        val priorCount = priorState?.totalRecords ?: priorSnapshot?.records?.size ?: 0
        val priorCorrupt = priorState?.corruptRecords ?: priorSnapshot?.corruptRecords ?: 0
        files.appendRecord(record)
        writeState(
            MemorySnapshot(
                records = emptyList(),
                corruptRecords = priorCorrupt,
                compactedAtEpochMs = priorState?.compactedAtEpochMs ?: priorSnapshot?.compactedAtEpochMs
            ),
            totalRecords = priorCount + 1
        )
        return record
    }

    fun rememberJob(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.JOB, title, body, tags, subjectType = "job", subjectId = subjectId)

    fun rememberSession(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.SESSION, title, body, tags, subjectType = "session", subjectId = subjectId)

    fun rememberThread(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.THREAD, title, body, tags, subjectType = "thread", subjectId = subjectId)

    fun rememberBatch(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.BATCH, title, body, tags, subjectType = "batch", subjectId = subjectId)

    fun rememberQueue(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.QUEUE, title, body, tags, subjectType = "queue", subjectId = subjectId)

    fun rememberRoute(subjectId: String?, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.ROUTE, title, body, tags, subjectType = "route", subjectId = subjectId)

    fun rememberFailure(subjectType: String, subjectId: String?, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.FAILURE, title, body, tags, subjectType = subjectType, subjectId = subjectId)

    fun rememberVerification(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.VERIFICATION, title, body, tags, subjectType = "verification", subjectId = subjectId)

    fun rememberRepair(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.REPAIR, title, body, tags, subjectType = "repair", subjectId = subjectId)

    fun rememberSourceDecision(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(
            MemoryKind.SOURCE,
            title,
            body,
            tags,
            subjectType = "source",
            subjectId = subjectId,
            sourceCoordinate = subjectId,
            authority = MemoryAuthority.SOURCE_REFERENCE
        )

    fun rememberToolResult(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.TOOL, title, body, tags, subjectType = "tool", subjectId = subjectId)

    fun rememberSummary(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.SUMMARY, title, body, tags, subjectType = "summary", subjectId = subjectId)

    fun rememberRecovery(subjectId: String, title: String, body: String, tags: List<String> = emptyList()): MemoryRecord =
        rememberDetailed(MemoryKind.RECOVERY, title, body, tags, subjectType = "recovery", subjectId = subjectId)

    fun rememberReward(
        subjectId: String,
        title: String,
        body: String,
        tags: List<String> = emptyList()
    ): MemoryRecord = rememberDetailed(
        MemoryKind.REWARD,
        title,
        body,
        tags,
        subjectType = "reward",
        subjectId = subjectId
    )

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

    /** Returns redacted, content-addressed source windows for optional indexing. */
    fun chunkSource(source: String): List<MemorySourceChunk> =
        sourceChunker.chunk(redactionFilter.redact(source))

    /**
     * Indexes redacted source chunks only when sqlite-vec is actually usable.
     * The caller supplies embeddings; this store never invents or treats them
     * as an authority source. Empty or unavailable optional backends degrade
     * to the existing local lexical/DLOI path.
     */
    fun indexSourceVectors(
        chunks: List<MemorySourceChunk>,
        embeddings: Map<String, List<Float>>
    ): SqliteVecMemoryIndex.IndexResult {
        if (!backends.sqliteVecAvailable()) {
            return SqliteVecMemoryIndex.IndexResult(0, null, "sqlite-vec unavailable")
        }
        return vectorIndex.index(chunks, embeddings)
    }

    fun searchSourceVectors(embedding: List<Float>, limit: Int = 10): List<SqliteVecMemoryIndex.VectorHit> {
        if (!backends.sqliteVecAvailable()) return emptyList()
        return vectorIndex.search(embedding, limit)
    }

    fun compact(maxRecords: Int = 1000): MemoryState {
        val safeLimit = maxRecords.coerceIn(1, 5000)
        val snapshot = readSnapshot()
        val compacted = snapshot.records
            .sortedByDescending { it.createdAtEpochMs }
            .distinctBy { listOf(it.kind.name, it.subjectType.orEmpty(), it.subjectId.orEmpty(), it.title, stableFingerprint(it.body)).joinToString("|") }
            .take(safeLimit)
            .sortedBy { it.createdAtEpochMs }
        files.replaceRecords(compacted)
        val next = snapshot.copy(records = compacted, compactedAtEpochMs = now())
        writeState(next)
        return next.toState()
    }

    fun status(): MemoryStatus {
        root.mkdirs()
        val snapshot = readSnapshot()
        val state = snapshot.toState()
        return MemoryStatus(
            root = root,
            jsonlFile = jsonlFile,
            stateFile = stateFile,
            totalRecords = state.totalRecords,
            corruptRecords = state.corruptRecords,
            schemaVersion = state.schemaVersion,
            sqliteAvailable = backends.commandExists("sqlite3"),
            sqliteVecAvailable = backends.sqliteVecAvailable(),
            pineconeConfigured = env["PINECONE_API_KEY"].isNullOrBlank().not(),
            supabaseConfigured = env["SUPABASE_URL"].isNullOrBlank().not() &&
                env["SUPABASE_ANON_KEY"].isNullOrBlank().not(),
            googleMetadataConfigured = env["GOOGLE_APPLICATION_CREDENTIALS"].isNullOrBlank().not() ||
                env["GOOGLE_OAUTH_CLIENT_SECRET"].isNullOrBlank().not()
        )
    }

    private fun cleanedTitle(kind: MemoryKind, title: String): String {
        val fallback = kind.name.lowercase(Locale.US)
        return redactionFilter.redact(title.trim().ifEmpty { fallback }).take(240)
    }

    private fun normalizeTags(tags: List<String>): List<String> =
        tags.map { redactionFilter.redact(it.trim().lowercase(Locale.US)) }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun stableFingerprint(value: String): String = redactionFilter.stableFingerprint(value)

    private fun stableId(
        kind: MemoryKind,
        title: String,
        body: String,
        createdAt: Long,
        subjectType: String?,
        subjectId: String?
    ): String {
        val material = listOf(kind.name, title, body, createdAt.toString(), subjectType.orEmpty(), subjectId.orEmpty()).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
    }

    /**
     * Records plus the corruption count, taking the worst of what is on disk
     * now and what a previous read recorded.
     *
     * The max is deliberate: compaction rewrites the log, so a corrupt line
     * that was dropped stops being visible in the file while still having
     * happened. Letting the count fall back to zero would erase the evidence.
     */
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

    private fun writeState(snapshot: MemorySnapshot, totalRecords: Int = snapshot.records.size) =
        files.writeState(totalRecords, snapshot.corruptRecords, snapshot.compactedAtEpochMs)

    private data class MemorySnapshot(
        val records: List<MemoryRecord>,
        val corruptRecords: Int,
        val compactedAtEpochMs: Long?
    ) {
        fun toState(): MemoryState =
            MemoryState(
                schemaVersion = MEMORY_SCHEMA_VERSION,
                totalRecords = records.size,
                corruptRecords = corruptRecords,
                compactedAtEpochMs = compactedAtEpochMs
            )
    }
}
