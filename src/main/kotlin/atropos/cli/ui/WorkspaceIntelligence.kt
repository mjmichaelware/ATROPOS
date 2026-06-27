/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class RepositoryState(
    val isRepository: Boolean,
    val branch: String?,
    val changedFiles: Int?,
    val available: Boolean
) {
    val clean: Boolean?
        get() = changedFiles?.let { it == 0 }

    companion object {
        fun unknown(): RepositoryState =
            RepositoryState(false, null, null, false)
    }
}

fun interface WorkspaceInspector {
    fun inspect(workspace: String): RepositoryState
}

class CachingGitWorkspaceInspector(
    private val cacheMillis: Long = 2_000,
    private val timeoutMillis: Long = 750,
    private val outputLimit: Int = 256 * 1024
) : WorkspaceInspector {
    private var cachedPath: String? = null
    private var cachedAt = 0L
    private var cachedState = RepositoryState.unknown()

    @Synchronized
    override fun inspect(workspace: String): RepositoryState {
        val now = System.currentTimeMillis()
        if (workspace == cachedPath && now - cachedAt < cacheMillis) {
            return cachedState
        }
        cachedPath = workspace
        cachedAt = now
        cachedState = inspectNow(workspace)
        return cachedState
    }

    private fun inspectNow(workspace: String): RepositoryState {
        val directory = File(workspace)
        if (!directory.isDirectory) return RepositoryState.unknown()

        val process = try {
            ProcessBuilder(
                listOf(
                    "git", "-C", directory.absolutePath,
                    "status", "--porcelain=v1", "--branch"
                )
            ).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return RepositoryState.unknown()
        }

        val pump = Executors.newSingleThreadExecutor { task ->
            Thread(task, "atropos-git-output").apply { isDaemon = true }
        }
        val output = pump.submit<String> {
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (result.length < outputLimit) {
                    val count = reader.read(
                        buffer,
                        0,
                        minOf(buffer.size, outputLimit - result.length)
                    )
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        }

        return try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
                output.cancel(true)
                RepositoryState.unknown()
            } else if (process.exitValue() != 0) {
                RepositoryState(false, null, null, true)
            } else {
                parse(output.get(250, TimeUnit.MILLISECONDS))
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            RepositoryState.unknown()
        } finally {
            pump.shutdownNow()
        }
    }

    private fun parse(output: String): RepositoryState {
        val lines = output.lineSequence().filter(String::isNotBlank).toList()
        val header = lines.firstOrNull()?.takeIf { it.startsWith("## ") }
            ?: return RepositoryState(false, null, null, true)
        val branchText = header.removePrefix("## ").substringBefore("...").trim()
        val branch = branchText.takeUnless {
            it.isBlank() || it == "HEAD (no branch)" || it == "No commits yet on"
        }
        val changes = lines.drop(1).count()
        return RepositoryState(true, branch, changes, true)
    }
}
