/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.core.journal.EventCategory
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * One event, reduced to what a filter needs and where to find the rest.
 *
 * The whole design turns on [byteOffset]. Source Doc 3 §5.3 requires history to
 * be "queryable without loading the entire trace into memory", and the only way
 * to honour that against an append-only journal is to decide *which* lines
 * matter before reading any of them. An entry is a few dozen bytes; the event it
 * points at may be four kilobytes. A run with 50,000 events has an index that
 * fits comfortably in memory on a phone and a journal that does not.
 */
data class HistoryIndexEntry(
    val runId: String,
    val sequence: Long,
    val byteOffset: Long,
    val role: ExecutionRole,
    val category: EventCategory,
    val failed: Boolean,
    val provider: String? = null,
    val task: String? = null,
    val source: String? = null,
    val requirement: String? = null
) {
    fun encode(): String = listOf(
        sequence.toString(),
        byteOffset.toString(),
        role.canonical,
        category.name,
        if (failed) "1" else "0",
        provider.orEmpty(),
        task.orEmpty(),
        source.orEmpty(),
        requirement.orEmpty()
    ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    companion object {
        fun decode(runId: String, line: String): HistoryIndexEntry? {
            val parts = line.split('\t')
            if (parts.size < 9) return null
            val sequence = parts[0].toLongOrNull() ?: return null
            val offset = parts[1].toLongOrNull() ?: return null
            val category = runCatching { EventCategory.valueOf(parts[3]) }.getOrNull() ?: return null
            return HistoryIndexEntry(
                runId = runId,
                sequence = sequence,
                byteOffset = offset,
                role = ExecutionRole.of(parts[2]),
                category = category,
                failed = parts[4] == "1",
                provider = parts[5].ifEmpty { null },
                task = parts[6].ifEmpty { null },
                source = parts[7].ifEmpty { null },
                requirement = parts[8].ifEmpty { null }
            )
        }
    }
}

/**
 * A seekable index over one run's journal.
 *
 * Built by scanning the journal once and recording, per event, the byte offset
 * of its line plus the fields queries filter on. Stored beside the journal as
 * `events.index`, so it survives restart — §5.3 requires that too, and rebuilding
 * a 50,000-event index on every process start would make history a thing an
 * operator avoids opening.
 *
 * The index is derived, never authoritative. If it is missing, stale or corrupt
 * the answer is to rebuild it from the journal, which is why [rebuild] is cheap
 * to call and [load] silently returns empty rather than throwing. A wrong index
 * must never be able to make the journal say something it does not say.
 */
class HistoryIndex(private val runsRoot: Path) {

    /**
     * Rebuilds the index for [runId] from its journal.
     *
     * Offsets are byte offsets into the journal file, accumulated as the scan
     * proceeds rather than derived from character counts — the journal is UTF-8
     * and a multi-byte character would put every subsequent offset out by the
     * difference, which fails silently and only for runs containing non-ASCII.
     */
    fun rebuild(runId: String): List<HistoryIndexEntry> {
        val journal = journalFile(runId)
        if (!Files.isRegularFile(journal)) return emptyList()

        val entries = mutableListOf<HistoryIndexEntry>()
        var offset = 0L
        Files.newBufferedReader(journal, StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val lineBytes = line.toByteArray(StandardCharsets.UTF_8).size.toLong() + 1
                entryOf(runId, line, offset)?.let(entries::add)
                offset += lineBytes
            }
        }
        write(runId, entries)
        return entries
    }

    /** The stored index, or a rebuild when it is absent or older than the journal. */
    fun load(runId: String): List<HistoryIndexEntry> {
        val index = indexFile(runId)
        val journal = journalFile(runId)
        if (!Files.isRegularFile(journal)) return emptyList()
        if (!Files.isRegularFile(index)) return rebuild(runId)
        if (Files.getLastModifiedTime(index) < Files.getLastModifiedTime(journal)) {
            return rebuild(runId)
        }
        val stored = runCatching { Files.readAllLines(index, StandardCharsets.UTF_8) }
            .getOrElse { return rebuild(runId) }
            .filter { it.isNotBlank() }
        val decoded = stored.mapNotNull { HistoryIndexEntry.decode(runId, it) }

        // Any unparseable line means the index cannot be trusted, and an index
        // is derived — so the answer is to rebuild rather than to serve a
        // partial view. Filtering the bad lines out instead would silently hide
        // events from every query, which is worse than a slow first read.
        if (decoded.size != stored.size) return rebuild(runId)
        return decoded
    }

    /**
     * Reads the journal lines named by [entries], and only those.
     *
     * One seek per entry. The alternative — reading the file and filtering — is
     * what §5.3 forbids, and the difference is not academic on a device where a
     * long autonomous run's journal is tens of megabytes.
     */
    fun readAt(runId: String, entries: List<HistoryIndexEntry>): List<String> {
        val journal = journalFile(runId)
        if (!Files.isRegularFile(journal) || entries.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        RandomAccessFile(journal.toFile(), "r").use { file ->
            val length = file.length()
            entries.forEach { entry ->
                if (entry.byteOffset < 0 || entry.byteOffset >= length) return@forEach
                file.seek(entry.byteOffset)
                // readLine decodes ISO-8859-1; re-encode to recover UTF-8 bytes
                // rather than mangling any non-ASCII the payload carried.
                val raw = file.readLine() ?: return@forEach
                lines += String(raw.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
            }
        }
        return lines
    }

    /** Run ids with a journal, newest first by modification time. */
    fun runIds(): List<String> {
        if (!Files.isDirectory(runsRoot)) return emptyList()
        return Files.list(runsRoot).use { stream ->
            stream.filter { Files.isRegularFile(it.resolve("events.journal")) }
                .sorted { a, b ->
                    Files.getLastModifiedTime(b.resolve("events.journal"))
                        .compareTo(Files.getLastModifiedTime(a.resolve("events.journal")))
                }
                .map { it.fileName.toString() }
                .toList()
        }
    }

    private fun entryOf(runId: String, line: String, offset: Long): HistoryIndexEntry? {
        val record = atropos.core.journal.EventJournalRecord.fromJournalLine(line) ?: return null
        val event = ExecutionEvent.fromJournalRecord(record)
        return HistoryIndexEntry(
            runId = runId,
            sequence = event.sequence,
            byteOffset = offset,
            role = event.role,
            category = event.category,
            failed = event.category == EventCategory.ERROR || event.category == EventCategory.FAILURE,
            provider = event.provider,
            task = event.task,
            source = event.source,
            requirement = event.requirement
        )
    }

    private fun write(runId: String, entries: List<HistoryIndexEntry>) {
        val index = indexFile(runId)
        runCatching {
            Files.createDirectories(index.parent)
            Files.write(
                index,
                entries.map { it.encode() },
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        }
    }

    private fun journalFile(runId: String): Path = runsRoot.resolve(runId).resolve("events.journal")

    private fun indexFile(runId: String): Path = runsRoot.resolve(runId).resolve("events.index")
}
