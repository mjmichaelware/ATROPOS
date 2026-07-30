package atropos.core.memory

import java.io.File

const val MEMORY_SCHEMA_VERSION = 2

enum class MemoryKind {
    NOTE,
    CODE,
    ROUTE,
    FAILURE,
    SOURCE,
    DECISION,
    SESSION,
    THREAD,
    BATCH,
    JOB,
    QUEUE,
    VERIFICATION,
    REPAIR,
    TOOL,
    SUMMARY,
    RECOVERY,
    REWARD
}

enum class MemoryAuthority {
    OBSERVATION,
    SOURCE_REFERENCE,
    PROPOSAL_REFERENCE
}

data class MemoryRecord(
    val id: String,
    val kind: MemoryKind,
    val title: String,
    val body: String,
    val tags: List<String>,
    val createdAtEpochMs: Long,
    val subjectType: String? = null,
    val subjectId: String? = null,
    val contentSha256: String = "",
    val failureSignature: String? = null,
    val sourceCoordinate: String? = null,
    val authority: MemoryAuthority = MemoryAuthority.OBSERVATION,
    val schemaVersion: Int = MEMORY_SCHEMA_VERSION,
    val redacted: Boolean = true
)

data class MemorySearchHit(
    val record: MemoryRecord,
    val score: Int
)

data class MemoryState(
    val schemaVersion: Int,
    val totalRecords: Int,
    val corruptRecords: Int,
    val compactedAtEpochMs: Long? = null
)

data class MemoryStatus(
    val root: File,
    val jsonlFile: File,
    val stateFile: File,
    val totalRecords: Int,
    val corruptRecords: Int,
    val schemaVersion: Int,
    val sqliteAvailable: Boolean,
    val sqliteVecAvailable: Boolean,
    val pineconeConfigured: Boolean,
    val supabaseConfigured: Boolean,
    val googleMetadataConfigured: Boolean
)
