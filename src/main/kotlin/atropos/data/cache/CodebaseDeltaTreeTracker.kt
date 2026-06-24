package atropos.data.cache

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class FileDelta(
    val relativePath: String,
    val modificationType: String, // "MODIFIED", "ADDED", "DELETED", "UNTRACKED"
    val impactedLinesCount: Int
)

class CodebaseDeltaTreeTracker(private val repositoryRoot: String = ".") {

    /**
     * Executes an inline porcelain status parse pass against the local workspace 
     * to isolate changed paths and file modifications.
     */
    fun getActiveWorkspaceDeltas(): List<FileDelta> {
        val deltas = mutableListOf<FileDelta>()
        try {
            // Execute non-blocking porcelain status stream pass
            val process = ProcessBuilder("git", "status", "--porcelain")
                .directory(File(repositoryRoot))
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.length < 4) continue

                val statusCode = currentLine.substring(0, 2).trim()
                val filePath = currentLine.substring(3).trim()
                
                val type = when (statusCode) {
                    "M"  -> "MODIFIED"
                    "A"  -> "ADDED"
                    "D"  -> "DELETED"
                    "??" -> "UNTRACKED"
                    else -> "MODIFIED"
                }

                val linesChanged = calculateFileDiffPayload(filePath, type)
                deltas.add(FileDelta(filePath, type, linesChanged))
            }
            process.waitFor()
        } catch (e: Exception) {
            println("\u001B[33m[Tracker Warning] Failed to interface with local Git stream: ${e.message}\u001B[0m")
        }
        return deltas
    }

    /**
     * Computes the scale of code modifications using an analytical line count 
     * step pass to prevent token context explosion downstream.
     */
    private fun calculateFileDiffPayload(filePath: String, type: String): Int {
        if (type == "DELETED") return 0
        val targetFile = File(repositoryRoot, filePath)
        if (!targetFile.exists()) return 0

        return try {
            if (type == "UNTRACKED" || type == "ADDED") {
                targetFile.readLines().size
            } else {
                // Parse line modifications for altered tracking sheets
                val process = ProcessBuilder("git", "diff", "--numstat", filePath)
                    .directory(File(repositoryRoot))
                    .start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readLine() ?: ""
                process.waitFor()
                
                val parts = output.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val additions = parts[0].toIntOrNull() ?: 0
                    val deletions = parts[1].toIntOrNull() ?: 0
                    additions + deletions
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            0
        }
    }
}
