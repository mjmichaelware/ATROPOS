/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import atropos.core.AtroposRepoRootLocator
import atropos.core.TargetWorkspaceLocator
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class GitCommandResult(val exitCode: Int, val stdout: String, val stderr: String)

/**
 * LocalGitMcpTool: Executes git operations strictly bounded to isolated target workspaces.
 * Pathing is decoupled from engine authority root via TargetWorkspaceLocator.
 */
class LocalGitMcpTool(
    workspacePath: Path? = null
) {
    private val repoRoot: Path = if (workspacePath != null) {
        val engineRoot = AtroposRepoRootLocator.resolve().toAbsolutePath().normalize()
        val abs = workspacePath.toAbsolutePath().normalize()
        if (abs == engineRoot) {
            TargetWorkspaceLocator.resolveTargetWorkspace(null)
        } else {
            workspacePath
        }
    } else {
        TargetWorkspaceLocator.resolveTargetWorkspace(null)
    }

    private fun execute(vararg args: String): GitCommandResult {
        val engineRoot = AtroposRepoRootLocator.resolve().toAbsolutePath().normalize()
        val currentAbs = repoRoot.toAbsolutePath().normalize()
        if (currentAbs == engineRoot && args.any { it in setOf("add", "commit", "rebase", "checkout", "rm") }) {
            throw SecurityException("Engine domain write violation: mutating commands forbidden on engine root")
        }
        atropos.core.thinking.Thinking.detail("mcp", "Local git cmd: git ${args.joinToString(" ")}")
        val pb = ProcessBuilder("git", *args)
        pb.directory(repoRoot.toFile())
        val proc = pb.start()
        val stdout = proc.inputStream.bufferedReader().readText()
        val stderr = proc.errorStream.bufferedReader().readText()
        proc.waitFor(10, TimeUnit.SECONDS)
        return GitCommandResult(proc.exitValue(), stdout, stderr)
    }

    private fun assertSafeRelativePath(relPath: String) {
        val normalized = relPath.replace('\\', '/').trim()
        if (normalized.startsWith("../") || normalized.contains("/../") || normalized.startsWith(".git")) {
            throw SecurityException("Sandbox violation: Path traversal or .git access denied: $relPath")
        }
    }

    fun statusPorcelain(): List<String> {
        atropos.core.thinking.Thinking.step("mcp", "Querying local git status")
        val res = execute("status", "--porcelain=v1")
        if (res.exitCode != 0) return emptyList()
        return res.stdout.lines().filter { it.isNotBlank() }
    }

    fun diff(paths: List<String>): String {
        paths.forEach { assertSafeRelativePath(it) }
        atropos.core.thinking.Thinking.step("mcp", "Generating diff for ${paths.size} path(s)")
        val cmd = mutableListOf("diff", "--no-color", "--")
        cmd.addAll(paths)
        return execute(*cmd.toTypedArray()).stdout
    }

    fun log(maxCount: Int = 10): List<String> {
        atropos.core.thinking.Thinking.step("mcp", "Querying local git log")
        val res = execute("log", "--oneline", "-n", maxCount.coerceIn(1, 100).toString())
        if (res.exitCode != 0) return emptyList()
        return res.stdout.lines().filter { it.isNotBlank() }
    }

    fun add(paths: List<String>): GitCommandResult {
        paths.forEach { assertSafeRelativePath(it) }
        val cmd = mutableListOf("add", "--")
        cmd.addAll(paths)
        return execute(*cmd.toTypedArray())
    }

    fun commit(message: String): GitCommandResult {
        require(message.isNotBlank()) { "Commit message cannot be blank" }
        atropos.core.thinking.Thinking.step("mcp", "Creating git commit: ${message.take(60)}")
        return execute("commit", "-m", message)
    }
}
