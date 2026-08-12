/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.policy.BoundedProcessRunner
import java.io.File
import java.nio.file.Path

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
    private val processRunner = BoundedProcessRunner()
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

        val result = runCatching {
            processRunner.run(
                command = listOf("git", "-C", directory.absolutePath, "status", "--porcelain=v1", "--branch"),
                directory = Path.of("/"),
                timeoutMillis = timeoutMillis,
                maxOutputBytes = outputLimit.coerceAtMost(256 * 1024),
                maxOutputLines = 4_000
            )
        }.getOrNull() ?: return RepositoryState.unknown()
        if (result.timedOut || result.launchError != null) return RepositoryState.unknown()
        if (result.exitCode != 0) return RepositoryState(false, null, null, true)
        return parse(result.stdout + result.stderr)
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
