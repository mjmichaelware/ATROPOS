/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.nio.file.Files
import java.nio.file.Path

/**
 * How many bytes the device will actually accept.
 *
 * `SUP.STOR.FREE-SPACE-GATE` calls for "portable free-space APIs for
 * Android/Termux vs desktop". There is exactly one that works everywhere the
 * JVM runs: `FileStore.getUsableSpace`, which reports what *this process* may
 * write rather than what is unallocated. On Android those differ — reserved
 * blocks and per-app quota mean a device showing gigabytes free will refuse a
 * write long before they are gone — and `getUsableSpace` is the number that
 * respects them.
 *
 * The device number and the declared ceiling are different limits and both
 * bind. [StorageConstitution] is a promise the operator made about how much
 * ATROPOS may use; this is a fact about the disk. Passing one says nothing
 * about the other, which is why the gate consults both.
 */
class FreeSpaceProbe(private val anchor: Path) {

    /**
     * @return null when the filesystem cannot be interrogated. Callers must
     *   treat that as unknown, never as plenty — an unreadable filestore on a
     *   phone is more likely to be a full or unmounted one than a healthy one.
     */
    fun usableBytes(): Long? = runCatching {
        Files.getFileStore(existingAnchor()).usableSpace
    }.getOrNull()

    fun totalBytes(): Long? = runCatching {
        Files.getFileStore(existingAnchor()).totalSpace
    }.getOrNull()

    /** True when a write of [bytes] would leave less than [reserveBytes] behind. */
    fun wouldExhaustDevice(bytes: Long, reserveBytes: Long = DEFAULT_RESERVE_BYTES): Boolean {
        val usable = usableBytes() ?: return true
        return usable - bytes < reserveBytes
    }

    /**
     * The nearest existing ancestor of the anchor.
     *
     * The anchor is often a directory ATROPOS is about to create, and
     * `getFileStore` on a path that does not exist throws. Walking up finds the
     * filestore the new directory will land on, which is the one that matters.
     */
    private fun existingAnchor(): Path {
        var current: Path? = anchor.toAbsolutePath().normalize()
        while (current != null && !Files.exists(current)) current = current.parent
        return current ?: anchor.toAbsolutePath().root ?: anchor
    }

    companion object {
        /**
         * Headroom kept back from the device unconditionally.
         *
         * A phone that fills completely stops being able to do the things
         * needed to un-fill it. 256 MiB is enough to write a log, run a
         * collector, and hold the evidence explaining what happened.
         */
        const val DEFAULT_RESERVE_BYTES: Long = 256L * 1024 * 1024
    }
}
