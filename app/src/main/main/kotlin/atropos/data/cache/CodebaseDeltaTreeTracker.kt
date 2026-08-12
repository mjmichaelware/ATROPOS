/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.cache

import java.io.File
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

data class FileDelta(
    val relativePath: String,
    val linesChanged: Int,
    val status: String
)

data class TreeNode(
    val label: String,
    val children: List<TreeNode>
)

class CodebaseDeltaTreeTracker(
    private val workspacePath: String =
        System.getProperty("user.dir")
) {
    fun getActiveWorkspaceDeltas(): List<FileDelta> {
        val root = File(workspacePath)
        if (!File(root, ".git").exists()) return emptyList()

        return try {
            val statusBytes = runProcessBytes(
                root,
                listOf(
                    "git",
                    "status",
                    "--porcelain=v1",
                    "-z"
                ),
                timeoutSeconds = 2
            )

            parsePorcelainZ(statusBytes)
                .map { entry ->
                    FileDelta(
                        relativePath = entry.path,
                        linesChanged = changedLineEstimate(
                            root,
                            entry.path,
                            entry.status
                        ),
                        status = entry.status
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun computeWorkspaceTreeEditDistance(): Int {
        val root = File(workspacePath)
        return getActiveWorkspaceDeltas()
            .filterNot {
                it.status.contains("D")
            }
            .sumOf { delta ->
                calculateTED(
                    buildTreeFromGitHead(root, delta.relativePath),
                    buildTreeFromFile(File(root, delta.relativePath))
                )
            }
    }

    private data class PorcelainEntry(
        val status: String,
        val path: String
    )

    private fun parsePorcelainZ(bytes: ByteArray): List<PorcelainEntry> {
        if (bytes.isEmpty()) return emptyList()

        val fields = bytes
            .toString(Charsets.UTF_8)
            .split('\u0000')
            .filter { it.isNotEmpty() }

        val output = mutableListOf<PorcelainEntry>()
        var index = 0

        while (index < fields.size) {
            val entry = fields[index]
            if (entry.length < 4) {
                index++
                continue
            }

            val xy = entry.substring(0, 2)
            val status = xy.trim().ifEmpty { xy }
            val path = entry.substring(3)

            output += PorcelainEntry(status, path)

            val isRenameOrCopy =
                xy[0] == 'R' ||
                    xy[1] == 'R' ||
                    xy[0] == 'C' ||
                    xy[1] == 'C'

            index += if (isRenameOrCopy) 2 else 1
        }

        return output
    }

    private fun changedLineEstimate(
        root: File,
        path: String,
        status: String
    ): Int {
        val file = File(root, path)

        if (status == "??" || status.contains("A")) {
            return safeReadLines(file).size
        }

        if (status.contains("D")) return 0

        return try {
            val output = runProcessText(
                root,
                listOf(
                    "git",
                    "diff",
                    "--numstat",
                    "HEAD",
                    "--",
                    path
                ),
                timeoutSeconds = 1
            ).trim()

            if (output.isEmpty()) return 0

            val parts = output.split(Regex("\\s+"))
            val added = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val removed = parts.getOrNull(1)?.toIntOrNull() ?: 0
            added + removed
        } catch (_: Exception) {
            0
        }
    }

    private fun buildTreeFromFile(file: File): TreeNode {
        if (!file.exists() || !file.isFile) {
            return TreeNode("EMPTY", emptyList())
        }

        return TreeNode(
            file.name,
            safeReadLines(file)
                .filter { it.isNotBlank() }
                .map {
                    TreeNode(
                        it.trim().hashCode().toString(),
                        emptyList()
                    )
                }
        )
    }

    private fun buildTreeFromGitHead(
        root: File,
        path: String
    ): TreeNode {
        return try {
            val content = runProcessText(
                root,
                listOf("git", "show", "HEAD:$path"),
                timeoutSeconds = 1
            )

            if (content.isEmpty()) {
                TreeNode("EMPTY", emptyList())
            } else {
                TreeNode(
                    File(path).name,
                    content.lines()
                        .filter { it.isNotBlank() }
                        .map {
                            TreeNode(
                                it.trim().hashCode().toString(),
                                emptyList()
                            )
                        }
                )
            }
        } catch (_: Exception) {
            TreeNode("EMPTY", emptyList())
        }
    }

    private fun calculateTED(t1: TreeNode, t2: TreeNode): Int {
        val rootCost = if (t1.label == t2.label) 0 else 1
        val rows = t1.children.size
        val cols = t2.children.size
        val dp = Array(rows + 1) { IntArray(cols + 1) }

        for (i in 0..rows) dp[i][0] = i
        for (j in 0..cols) dp[0][j] = j

        for (i in 1..rows) {
            for (j in 1..cols) {
                val replaceCost =
                    if (
                        t1.children[i - 1].label ==
                        t2.children[j - 1].label
                    ) 0 else 1

                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + replaceCost
                )
            }
        }

        return rootCost + dp[rows][cols]
    }

    private fun safeReadLines(file: File): List<String> {
        if (!file.exists() || !file.isFile) return emptyList()
        return try {
            val decoder = Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)

            java.io.InputStreamReader(
                file.inputStream(),
                decoder
            ).use { reader ->
                reader.readLines()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun runProcessText(
        dir: File,
        command: List<String>,
        timeoutSeconds: Long
    ): String {
        return runProcessBytes(dir, command, timeoutSeconds)
            .toString(Charsets.UTF_8)
    }

    private fun runProcessBytes(
        dir: File,
        command: List<String>,
        timeoutSeconds: Long
    ): ByteArray {
        val process = ProcessBuilder(command)
            .directory(dir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        val bytes = process.inputStream.readBytes()

        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return ByteArray(0)
        }

        if (process.exitValue() != 0) return ByteArray(0)
        return bytes
    }
}
