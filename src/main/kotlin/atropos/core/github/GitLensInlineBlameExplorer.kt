/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * ATOM EX2: GITLENS INLINE REPOSITORY BLAME AND HEATMAP EXPLORER
 * Path: src/main/kotlin/atropos/core/github/GitLensInlineBlameExplorer.kt
 *
 * Renders subtle inline annotations at the end of the active code line showing
 * author, commit age, and message, with on-hover heatmaps and commit graphs.
 */
data class LineBlameAnnotation(
    val lineNumber: Int,
    val commitSha: String,
    val authorName: String,
    val authorTimestamp: Instant,
    val commitSummary: String
) {
    fun renderInlineGhostText(): String {
        val shortSha = commitSha.take(7)
        val relativeAge = formatRelativeAge(authorTimestamp)
        return "• $authorName, $relativeAge • $commitSummary ($shortSha)"
    }

    private fun formatRelativeAge(time: Instant): String {
        val diffSeconds = Instant.now().epochSecond - time.epochSecond
        val days = diffSeconds / 86400
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 30L -> "${days}d ago"
            days < 365L -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }
}

data class FileHeatmapDistribution(
    val filePath: String,
    val totalLines: Int,
    val authorContributions: Map<String, Int>,
    val commitFrequencies: Map<String, Int>,
    val mostRecentCommit: Instant?
)

class GitLensInlineBlameExplorer {
    private val blameCache = ConcurrentHashMap<String, Map<Int, LineBlameAnnotation>>()

    fun parsePorcelainBlame(filePath: String, porcelainOutput: String): Map<Int, LineBlameAnnotation> {
        val result = mutableMapOf<Int, LineBlameAnnotation>()
        val lines = porcelainOutput.split('\n')
        var currentSha = ""
        var currentAuthor = ""
        var currentSummary = ""
        var currentTimestamp = Instant.now()
        var currentLineNum = 1

        for (line in lines) {
            val parts = line.split(' ')
            if (parts.size >= 4 && parts[0].length == 40) {
                currentSha = parts[0]
                currentLineNum = parts[2].toIntOrNull() ?: currentLineNum
            } else if (line.startsWith("author ")) {
                currentAuthor = line.removePrefix("author ").trim()
            } else if (line.startsWith("author-time ")) {
                val epoch = line.removePrefix("author-time ").trim().toLongOrNull() ?: Instant.now().epochSecond
                currentTimestamp = Instant.ofEpochSecond(epoch)
            } else if (line.startsWith("summary ")) {
                currentSummary = line.removePrefix("summary ").trim()
                result[currentLineNum] = LineBlameAnnotation(
                    lineNumber = currentLineNum,
                    commitSha = currentSha,
                    authorName = currentAuthor,
                    authorTimestamp = currentTimestamp,
                    commitSummary = currentSummary
                )
            }
        }

        blameCache[filePath] = result
        return result
    }

    fun getAnnotationForLine(filePath: String, lineNumber: Int): LineBlameAnnotation? {
        return blameCache[filePath]?.get(lineNumber)
    }

    fun computeHeatmap(filePath: String): FileHeatmapDistribution {
        val annotations = blameCache[filePath]?.values ?: emptyList()
        val authors = mutableMapOf<String, Int>()
        val commits = mutableMapOf<String, Int>()
        var latest: Instant? = null

        for (a in annotations) {
            authors[a.authorName] = (authors[a.authorName] ?: 0) + 1
            commits[a.commitSha] = (commits[a.commitSha] ?: 0) + 1
            if (latest == null || a.authorTimestamp.isAfter(latest)) {
                latest = a.authorTimestamp
            }
        }

        return FileHeatmapDistribution(
            filePath = filePath,
            totalLines = annotations.size,
            authorContributions = authors,
            commitFrequencies = commits,
            mostRecentCommit = latest
        )
    }

    companion object {
        val defaultExplorer = GitLensInlineBlameExplorer()
    }
}
