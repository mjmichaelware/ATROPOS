package atropos.core.memory

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The two files memory lives in, and how they survive a crash.
 *
 * A JSONL log of records plus a small `key=value` state file. Split from
 * [LocalMemoryStore] so that "how memory is stored" is separable from "what is
 * worth remembering" — the store composes this and keeps the semantics.
 *
 * ## Append for records, atomic replace for everything else
 *
 * A new record is appended, never rewritten, so recording a memory is O(1) and
 * cannot corrupt what is already there. Compaction and state updates go through
 * [atomicWrite]: temp file in the same directory, then move. A crash mid-write
 * leaves the previous complete file rather than a truncated one, which for the
 * record log would mean losing every memory before the cut, not just the one
 * being written.
 *
 * The move falls back to a non-atomic replace where the filesystem cannot
 * rename atomically.
 *
 * ## A corrupt line is counted, not fatal
 *
 * [readRecords] skips lines it cannot decode and counts them. Memory is
 * diagnostic data; a single bad line must not make the whole history
 * unreadable, and the count is surfaced so the damage is visible rather than
 * silently swallowed.
 */
class MemoryFileStore(
    private val root: File,
    private val jsonlFile: File,
    private val stateFile: File,
    private val now: () -> Long = System::currentTimeMillis
) {

    fun recordsExist(): Boolean = jsonlFile.exists()

    /** @return the decodable records and the number of lines that were not. */
    fun readRecords(): MemoryReadResult {
        if (!jsonlFile.exists()) return MemoryReadResult(emptyList(), 0)

        val records = mutableListOf<MemoryRecord>()
        var corrupt = 0
        val lines = runCatching { jsonlFile.readLines(StandardCharsets.UTF_8) }.getOrNull()
            ?: return MemoryReadResult(emptyList(), 0)

        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val decoded = MemoryRecordCodec.decode(line)
            if (decoded != null) records += decoded else corrupt++
        }
        return MemoryReadResult(records, corrupt)
    }

    /** @return null when no state file has been written yet. */
    fun readState(): MemoryState? {
        if (!stateFile.exists()) return null
        val lines = runCatching { stateFile.readLines(StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val fields = lines.mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()

        return MemoryState(
            schemaVersion = fields["schemaVersion"]?.toIntOrNull() ?: MEMORY_SCHEMA_VERSION,
            totalRecords = fields["totalRecords"]?.toIntOrNull() ?: 0,
            corruptRecords = fields["corruptRecords"]?.toIntOrNull() ?: 0,
            compactedAtEpochMs = fields["compactedAtEpochMs"]?.toLongOrNull()
        )
    }

    fun writeState(totalRecords: Int, corruptRecords: Int, compactedAtEpochMs: Long?) {
        root.mkdirs()
        atomicWrite(
            stateFile,
            buildString {
                appendLine("schemaVersion=$MEMORY_SCHEMA_VERSION")
                appendLine("totalRecords=$totalRecords")
                appendLine("corruptRecords=$corruptRecords")
                appendLine("compactedAtEpochMs=${compactedAtEpochMs ?: ""}")
            }
        )
    }

    fun appendRecord(record: MemoryRecord) {
        root.mkdirs()
        Files.writeString(
            jsonlFile.toPath(),
            MemoryRecordCodec.encode(record) + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.APPEND
        )
    }

    /** Replaces the whole log. Used by compaction, which drops the oldest records. */
    fun replaceRecords(records: List<MemoryRecord>) {
        root.mkdirs()
        val content = records.joinToString(separator = "\n") { MemoryRecordCodec.encode(it) }
            .let { if (it.isBlank()) "" else "$it\n" }
        atomicWrite(jsonlFile, content)
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.mkdirs()
        // Temp file in the target's own directory: a move across filesystems is
        // not atomic, so it must not land in the system temp dir.
        val temporary = File(target.parentFile, "${target.name}.${now()}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

/**
 * @param corruptRecords lines that could not be decoded. Surfaced rather than
 *   hidden so damage to the log is visible in status output.
 */
data class MemoryReadResult(
    val records: List<MemoryRecord>,
    val corruptRecords: Int
)
