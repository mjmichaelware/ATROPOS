/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact.export

import java.nio.file.Files
import java.nio.file.Path

/**
 * Finds the platform's downloads directory without hard-coding one.
 *
 * `SUP.PROV.ZERO-HARDCODED-PATHS`: "Same binary runs on phone and desktop
 * without path edits; portability is mechanical." Source Doc 5 states the same
 * requirement as the sovereign portability rule — "nothing hard coded to my
 * device or local".
 *
 * The candidates below are *probed*, not assumed. Each is a real location on a
 * platform this runs on, and the first one that exists and is writable wins. A
 * locator that returned a path it had not checked would produce a landing zone
 * that fails at write time, after the operator had already committed to the
 * export.
 *
 * Returning null is a correct outcome. `SUP.ART.ROOT-OR-DOWNLOADS` requires the
 * resolver to refuse rather than fall back, so "this platform has no downloads
 * directory" has to be expressible.
 */
object PlatformDownloadsLocator {

    /** @return the first writable candidate, or null when none is. */
    fun locate(env: (String) -> String? = System::getenv): Path? {
        candidates(env).forEach { candidate ->
            if (isUsable(candidate)) return candidate
        }
        return null
    }

    /**
     * In probe order, most specific first.
     *
     * `XDG_DOWNLOAD_DIR` comes first because it is the only one the user
     * actually declared; everything after it is a convention. Termux's shared
     * storage bridge is next, since on the target device it is the location a
     * file manager will find — a file written to the app-private home is
     * technically saved and practically invisible.
     */
    fun candidates(env: (String) -> String? = System::getenv): List<Path> = buildList {
        env("XDG_DOWNLOAD_DIR")?.takeIf { it.isNotBlank() }?.let { add(Path.of(it)) }
        env("ATROPOS_DOWNLOADS")?.takeIf { it.isNotBlank() }?.let { add(Path.of(it)) }

        val home = env("HOME")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }

        if (home != null) {
            val homePath = Path.of(home)
            // Termux exposes the device's shared storage here once
            // termux-setup-storage has run.
            add(homePath.resolve("storage/downloads"))
            add(homePath.resolve("storage/shared/Download"))
            add(homePath.resolve("Downloads"))
            add(homePath.resolve("Download"))
        }

        // Android's own path, for a JVM running outside Termux's home.
        add(Path.of("/sdcard/Download"))
    }

    private fun isUsable(path: Path): Boolean = runCatching {
        Files.isDirectory(path) && Files.isWritable(path)
    }.getOrDefault(false)
}
