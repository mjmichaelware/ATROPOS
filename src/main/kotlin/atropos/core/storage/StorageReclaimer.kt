/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

/**
 * The only code in the system that deletes to reclaim space.
 *
 * Both collectors delegate here rather than calling `Files.delete` themselves.
 * Recursive deletion is the single most dangerous operation ATROPOS performs,
 * and the containment check has to live in one place — two copies means one of
 * them eventually loses the check during an edit, and that copy deletes
 * somebody's repository.
 *
 * Every removal is bounded to [root]. A path that does not lie inside it is
 * refused, before symlinks are considered and again after resolution, because
 * a symlink inside the state directory pointing at `$HOME` would otherwise
 * make "reclaim the state directory" mean "delete the home directory".
 */
class StorageReclaimer(private val root: Path) {

    /**
     * @return bytes freed, or null when the target was refused or unreadable.
     */
    fun remove(target: Path): Long? {
        if (!contained(target)) return null
        if (!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return 0L

        val freed = sizeOf(target)
        val ok = runCatching {
            Files.walk(target).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { path ->
                    // Re-checked per entry. The walk started inside the root,
                    // but a symlink encountered mid-walk can resolve outside
                    // it, and following one into a delete is unrecoverable.
                    if (contained(path)) Files.deleteIfExists(path)
                }
            }
            true
        }.getOrDefault(false)

        return if (ok) freed else null
    }

    fun sizeOf(target: Path): Long = runCatching {
        if (Files.isRegularFile(target)) return Files.size(target)
        var total = 0L
        Files.walk(target).use { stream ->
            stream.forEach { path ->
                if (Files.isRegularFile(path)) total += runCatching { Files.size(path) }.getOrDefault(0L)
            }
        }
        total
    }.getOrDefault(0L)

    /**
     * Whether [candidate] lies strictly inside the root.
     *
     * The root itself is refused. "Reclaim everything" is not a garbage
     * collection, and a bug that produced the root as a candidate would
     * otherwise take the whole state directory with it.
     */
    private fun contained(candidate: Path): Boolean = runCatching {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val normalized = candidate.toAbsolutePath().normalize()
        if (normalized == normalizedRoot) return@runCatching false
        if (!normalized.startsWith(normalizedRoot)) return@runCatching false

        // Resolve what exists and re-check, so a symlink cannot widen the
        // bound between the lexical test and the delete.
        val real = runCatching { normalized.toRealPath() }.getOrNull() ?: return@runCatching true
        val realRoot = runCatching { normalizedRoot.toRealPath() }.getOrDefault(normalizedRoot)
        real != realRoot && real.startsWith(realRoot)
    }.getOrDefault(false)
}
