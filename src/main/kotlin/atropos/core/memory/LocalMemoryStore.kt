package atropos.core.memory

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
    private val writer = LocalMemoryWriter(root, now, redactionFilter, files, jsonlFile)
    private val recaller = LocalMemoryRecaller(root, now, redactionFilter, files)
    private val maintenance = LocalMemoryMaintenance(root, now, jsonlFile, stateFile, env, files, backends)

    companion object {
        fun defaultRoot(): File = AtroposRepoRootLocator.resolve().resolve(".atropos/memory").toFile()
    }

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
    ): MemoryRecord = writer.write(kind, title, body, tags, subjectType, subjectId, sourceCoordinate, authority)

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

    fun all(limit: Int = 200): List<MemoryRecord> = recaller.all(limit)

    fun latestByKind(kind: MemoryKind, limit: Int = 20): List<MemoryRecord> = recaller.latestByKind(kind, limit)

    fun findBySubject(subjectType: String, subjectId: String, limit: Int = 20): List<MemoryRecord> =
        recaller.findBySubject(subjectType, subjectId, limit)

    fun findBySubjectTypes(subjectTypes: Set<String>, limit: Int = 20): List<MemoryRecord> =
        recaller.findBySubjectTypes(subjectTypes, limit)

    fun search(query: String, limit: Int = 20): List<MemorySearchHit> = recaller.search(query, limit)

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
        val sanitizedEmbeddings = linkedMapOf<String, List<Float>>()
        val sanitizedChunks = chunks.map { chunk ->
            if (sha256(chunk.text) != chunk.sha256) {
                return SqliteVecMemoryIndex.IndexResult(0, null, "chunk hash does not match chunk text at index ${chunk.index}")
            }
            val sanitizedText = redactionFilter.redact(chunk.text)
            val sanitizedHash = sha256(sanitizedText)
            embeddings[chunk.sha256]?.let { vector -> sanitizedEmbeddings[sanitizedHash] = vector }
            chunk.copy(text = sanitizedText, sha256 = sanitizedHash)
        }
        return vectorIndex.index(sanitizedChunks, sanitizedEmbeddings)
    }

    fun searchSourceVectors(embedding: List<Float>, limit: Int = 10): List<SqliteVecMemoryIndex.VectorHit> {
        if (!backends.sqliteVecAvailable()) return emptyList()
        return vectorIndex.search(embedding, limit)
    }

    fun compact(maxRecords: Int = 1000): MemoryState = maintenance.compact(maxRecords)

    fun status(): MemoryStatus = maintenance.status()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
