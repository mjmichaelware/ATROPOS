/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.nio.file.Path

/**
 * ATOM W8: Global Text Search Directory Exclusion.
 *
 * Removes designated noisy directories (build outputs, package caches, minified bundles)
 * from global text search queries while permitting query-level overrides.
 *
 * ## Research: Ripgrep-Style Memory-Mapped Parallel Search
 * Researches memory-mapped file slices (`FileChannel.map`) combined with SIMD byte scanners
 * to search million-line repositories in sub-100 millisecond windows without heap exhaustion.
 */
object GlobalSearchDirectoryExclusionGate {

    fun defaultGate(): GlobalSearchDirectoryExclusionGate = this

    val DEFAULT_SEARCH_EXCLUDED_DIRS = setOf(
        ".git",
        "node_modules",
        ".gradle",
        "build",
        "dist",
        ".next",
        "out",
        "coverage",
        ".cache"
    )

    fun isSearchCandidate(path: Path, allowIgnored: Boolean = false): Boolean {
        if (allowIgnored) return true
        val normalized = path.toString().replace('\\', '/')
        for (dir in DEFAULT_SEARCH_EXCLUDED_DIRS) {
            if (normalized.contains("/$dir/") || normalized.endsWith("/$dir")) {
                return false
            }
        }
        return true
    }
}
