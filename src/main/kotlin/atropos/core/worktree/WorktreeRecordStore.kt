package atropos.core.worktree

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Where worktree meta files live, and the only way in and out of them.
 *
 * Split from [IsolatedWorktreeService] so that record persistence is separable
 * from worktree lifecycle. The service decides what a worktree may do; this
 * decides where its record is kept and how it survives a crash.
 *
 * ## Two properties this file exists to hold
 *
 * **An id can never escape the store directory.** [read] rejects any id
 * carrying a separator and then re-checks the resolved path against the root.
 * A worktree id reaches this from a job identifier, so treating it as a
 * filename without checking would make `../../etc/passwd` a readable record.
 *
 * **Writes are atomic.** The record is written to a temp file in the same
 * directory and moved into place, so a crash mid-write leaves the previous
 * record intact rather than a half-written one. A truncated meta file would
 * lose the baseline commit — the one field a rollback cannot proceed without.
 * The move falls back to a non-atomic replace when the filesystem cannot do an
 * atomic one, which is the usual case on some Android and network mounts.
 */
class WorktreeRecordStore(
    private val worktreeRoot: Path,
    private val codec: WorktreeRecordCodec = WorktreeRecordCodec()
) {

    /** The directory holding worktrees and their meta files. */
    fun root(): Path = worktreeRoot

    fun ensureRoot() {
        Files.createDirectories(worktreeRoot)
    }

    /** @return null when the id is unsafe, unknown, or its record is unreadable. */
    fun read(worktreeId: String): WorktreeRecord? {
        val file = metaFileFor(worktreeId) ?: return null
        if (!Files.isRegularFile(file)) return null
        return readFrom(file)
    }

    fun readFrom(file: Path): WorktreeRecord? {
        val lines = runCatching { Files.readAllLines(file, StandardCharsets.UTF_8) }.getOrNull()
            ?: return null
        return codec.decode(lines, file)
    }

    /** Newest first, skipping any record that cannot be parsed. */
    fun list(): List<WorktreeRecord> {
        if (!Files.isDirectory(worktreeRoot)) return emptyList()
        val files = runCatching {
            Files.list(worktreeRoot).use { stream -> stream.toList() }
        }.getOrNull() ?: return emptyList()

        return files
            .filter { isMetaFile(it) }
            .mapNotNull(::readFrom)
            .sortedByDescending { it.createdAt }
    }

    fun write(record: WorktreeRecord) {
        ensureRoot()
        val temporary = Files.createTempFile(worktreeRoot, record.id, TEMP_SUFFIX)
        Files.writeString(temporary, codec.encode(record), StandardCharsets.UTF_8)
        try {
            Files.move(
                temporary,
                record.metaFile,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            // Filesystems without atomic rename still need the record written.
            Files.move(temporary, record.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun delete(record: WorktreeRecord): Boolean =
        runCatching { Files.deleteIfExists(record.metaFile) }.getOrDefault(false)

    /**
     * The meta path for an id, or null when the id is not a safe single segment.
     *
     * A `.meta` suffix already present is tolerated so a caller can pass either
     * the id or the filename it saw in a listing.
     */
    fun metaFileFor(worktreeId: String): Path? {
        val id = worktreeId.trim().removeSuffix(META_SUFFIX)
        if (id.isBlank() || id.contains('/') || id.contains('\\')) return null
        val file = worktreeRoot.resolve("$id$META_SUFFIX").normalize()
        // Re-checked after normalisation: the separator test above rejects the
        // obvious forms, this catches whatever the platform resolved differently.
        if (!file.startsWith(worktreeRoot)) return null
        return file
    }

    private fun isMetaFile(path: Path): Boolean {
        val name = path.fileName.toString()
        return name.endsWith(META_SUFFIX) && name.startsWith(ID_PREFIX)
    }

    private companion object {
        const val META_SUFFIX = ".meta"
        const val TEMP_SUFFIX = ".tmp"

        /** Worktree ids are minted with this prefix; anything else is not ours. */
        const val ID_PREFIX = "wt-"
    }
}
