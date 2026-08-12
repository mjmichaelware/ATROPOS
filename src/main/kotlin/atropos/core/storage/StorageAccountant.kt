/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Measures what ATROPOS is actually storing, per class.
 *
 * `SUP.STOR.GLOBAL-BYTE-CEILING` names the accounting: "worktrees + evidence +
 * CAS + provider caches + logs". [StorageConstitution] can already report a
 * ceiling against a usage figure; nothing produced the usage figure, which is
 * why the ceiling had no effect on anything.
 *
 * One directory under `.atropos/` is one class. That mapping is not arbitrary
 * — it is how the tree is already laid out, so the accounting stays true when
 * a subsystem adds a file without telling anyone.
 *
 * Measurement is a walk, and a walk on phone-class storage is not free. It is
 * done on demand rather than on a timer, and every caller is a place where the
 * cost is already justified: a gate about to permit a large write, or an
 * operator who asked.
 */
class StorageAccountant(
    private val stateRoot: Path,
    private val policy: RetentionPolicy = RetentionPolicy()
) {
    /**
     * The current constitution: declared ceiling, measured classes, live tiers.
     *
     * @param ceilingBytes the operator's declared limit.
     */
    fun measure(ceilingBytes: Long, now: Instant = Instant.now()): StorageConstitution {
        val measured = classDirectories().map { directory ->
            val name = directory.fileName.toString()
            val usage = usageOf(directory)
            MeasuredClass(name, usage.bytes, usage.newest)
        }

        val total = measured.sumOf { it.bytes }
        val pressure = if (ceilingBytes <= 0) 1.0 else total.toDouble() / ceilingBytes.toDouble()

        return StorageConstitution(
            ceilingBytes = ceilingBytes,
            classes = measured.map { measured ->
                StorageClass(
                    id = measured.id,
                    tier = policy.tierFor(
                        storageClass = measured.id,
                        age = measured.newest?.let { ageOf(it, now) } ?: Duration.ZERO,
                        // The accountant does not know what is open. It reports
                        // the tier a class would hold if nothing referenced it;
                        // the collectors, which do know, re-check per item
                        // before removing anything.
                        referenced = false,
                        pressure = pressure
                    ),
                    bytes = measured.bytes
                )
            }.sortedByDescending { it.bytes }
        )
    }

    /** Directories directly under the state root, each one a storage class. */
    private fun classDirectories(): List<Path> {
        if (!Files.isDirectory(stateRoot)) return emptyList()
        return runCatching {
            Files.list(stateRoot).use { stream ->
                stream.toList().filter { Files.isDirectory(it) }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Total bytes and most recent modification beneath [directory].
     *
     * Unreadable entries are skipped rather than failing the measurement. A
     * single permission error part-way through a phone's storage should make
     * the number slightly low, not make the gate blind.
     */
    private fun usageOf(directory: Path): DirectoryUsage {
        var bytes = 0L
        var newest: Instant? = null
        runCatching {
            Files.walk(directory).use { stream ->
                stream.forEach { path ->
                    if (!Files.isRegularFile(path)) return@forEach
                    runCatching {
                        bytes += Files.size(path)
                        val modified = Files.getLastModifiedTime(path).toInstant()
                        if (newest == null || modified.isAfter(newest)) newest = modified
                    }
                }
            }
        }
        return DirectoryUsage(bytes, newest)
    }

    private data class DirectoryUsage(val bytes: Long, val newest: Instant?)

    private data class MeasuredClass(val id: String, val bytes: Long, val newest: Instant?)
}
