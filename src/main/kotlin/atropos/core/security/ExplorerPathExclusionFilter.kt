/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * ATOM W2: Explorer and Search Exclusion Filter.
 *
 * Hides specified file glob patterns and directories from the Explorer tree and search indexes,
 * while ensuring critical repository configurations (.gitignore, .github) remain explicitly accessible.
 *
 * ## Research: Glob-to-Regex Compilation Caching
 * Researches compiling Ant-style globs (`classes` or `build` trees) into thread-safe cached `java.util.regex.Pattern`
 * instances to evaluate deep directory hierarchies against exclusion lists in microsecond timeframes.
 */
object ExplorerPathExclusionFilter {

    private val patternCache = ConcurrentHashMap<String, Pattern>()

    val DEFAULT_EXCLUDED_DIRS = setOf(
        ".git",
        "node_modules",
        ".gradle",
        "build",
        ".next",
        ".turbo",
        "target"
    )

    val CRITICAL_UNBLOCKED_FILES = setOf(
        ".gitignore",
        ".gitattributes",
        "build.gradle.kts",
        "package.json",
        "tsconfig.json",
        "AGENTS.md"
    )

    fun isPathExcluded(relativePath: String, customExclusions: List<String> = emptyList()): Boolean {
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        val fileName = normalized.substringAfterLast('/')

        // Critical configuration files are never excluded
        if (CRITICAL_UNBLOCKED_FILES.contains(fileName)) {
            return false
        }

        // Check canonical excluded directory segments
        val segments = normalized.split('/')
        for (seg in segments) {
            if (DEFAULT_EXCLUDED_DIRS.contains(seg)) {
                return true
            }
        }

        // Evaluate custom glob expressions
        for (pattern in customExclusions) {
            val compiled = patternCache.computeIfAbsent(pattern) { glob ->
                val regex = glob
                    .replace(".", "\\.")
                    .replace("**", ".*")
                    .replace("*", "[^/]*")
                Pattern.compile("^$regex$")
            }
            if (compiled.matcher(normalized).matches()) {
                return true
            }
        }

        return false
    }
}
