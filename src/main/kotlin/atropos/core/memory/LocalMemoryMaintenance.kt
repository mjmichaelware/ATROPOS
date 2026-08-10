package atropos.core.memory

import java.io.File
import java.security.MessageDigest

internal class LocalMemoryMaintenance(
    private val root: File,
    private val now: () -> Long,
    private val jsonlFile: File,
    private val stateFile: File,
    private val env: Map<String, String>,
    private val files: MemoryFileStore,
    private val backends: MemoryBackendProbe
) {
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

    private fun stableFingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(16)
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
