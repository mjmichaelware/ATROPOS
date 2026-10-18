/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import atropos.core.AtroposRepoRootLocator
import java.io.File
import java.nio.file.Path

/**
 * ATOM-INGEST-020: Alphabetical Root Pull & File Mention Expander (@).
 *
 * Scans root files alphabetically for @-mentions, provides candidate
 * autocompletion with alphabetical ordering, and supports Arrow-Right
 * expansion of truncated/long file paths.
 */
class FileMentionExpander(
    private val rootDirectory: Path = defaultRoot()
) {
    data class MentionCandidate(
        val displayPath: String,
        val fullPath: String,
        val isDirectory: Boolean,
        val isTruncated: Boolean = false
    )

    companion object {
        fun defaultRoot(): Path =
            runCatching { AtroposRepoRootLocator.resolve() }.getOrNull()
                ?: Path.of(System.getProperty("user.dir", "."))
    }

    fun listCandidates(query: String = "", maxResults: Int = 32): List<MentionCandidate> {
        val needle = query.removePrefix("@").trim().lowercase()
        val dir = rootDirectory.toFile()
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val candidates = mutableListOf<MentionCandidate>()

        runCatching {
            dir.walkTopDown()
                .maxDepth(4)
                .filter { f ->
                    !f.name.startsWith(".") &&
                    !f.path.contains("/build/") &&
                    !f.path.contains("/.gradle/")
                }
                .forEach { file ->
                    val relPath = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                    if (needle.isEmpty() || relPath.lowercase().contains(needle)) {
                        candidates.add(
                            MentionCandidate(
                                displayPath = if (file.isDirectory) "$relPath/" else relPath,
                                fullPath = file.absolutePath,
                                isDirectory = file.isDirectory
                            )
                        )
                    }
                }
        }

        return candidates
            .sortedWith(compareBy({ !it.isDirectory }, { it.displayPath.lowercase() }))
            .take(maxResults)
    }

    fun expandTruncatedName(candidate: MentionCandidate, availableWidth: Int): MentionCandidate {
        val display = candidate.displayPath
        val isTruncated = display.length > availableWidth
        return candidate.copy(isTruncated = isTruncated)
    }

    fun fullExpansion(candidate: MentionCandidate): String = candidate.displayPath
}
