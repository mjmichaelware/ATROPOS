/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.nio.file.Path

/**
 * ATOM W3: File System Watcher Exclusion Subsystem.
 *
 * Excludes high-churn directories (node_modules, build caches, .git object stores) from live filesystem
 * watchers, preventing event queue overflow and eliminating CPU spikes during builds.
 *
 * ## Research: Kernel Event Filter Flags (inotify / FSEvents)
 * Researches omitting inotify watch descriptors on directories matching canonical ignore sets
 * (`IN_DONT_FOLLOW`, `FSEventStreamCreateFlagIgnoreSelf`) to filter events at kernel boundary.
 */
object FileSystemWatcherExclusionPolicy {

    fun defaultPolicy(): FileSystemWatcherExclusionPolicy = this

    private val WATCHER_EXCLUSION_PATTERNS = setOf(
        "node_modules",
        ".gradle",
        "build",
        ".next",
        ".git",
        "*.tmp",
        "*.log",
        "compile_log.zip"
    )

    fun shouldWatchPath(path: Path): Boolean {
        val stringPath = path.toString().replace('\\', '/')
        for (pattern in WATCHER_EXCLUSION_PATTERNS) {
            if (pattern.startsWith("*.")) {
                val ext = pattern.substring(1)
                if (stringPath.endsWith(ext)) return false
            } else {
                if (stringPath.contains("/$pattern/") || stringPath.endsWith("/$pattern")) {
                    return false
                }
            }
        }
        return true
    }
}
