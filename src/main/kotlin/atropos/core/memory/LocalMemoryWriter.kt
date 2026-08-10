package atropos.core.memory

import atropos.core.security.RedactionFilter
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal class LocalMemoryWriter(
    private val root: File,
    private val now: () -> Long,
    private val redactionFilter: RedactionFilter,
    private val files: MemoryFileStore,
    private val jsonlFile: File
) {
    fun write(
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
    )
}
