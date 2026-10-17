package atropos.core.integration

import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class GitCommandResult(val exitCode: Int, val stdout: String, val stderr: String)

class LocalGitMcpTool(private val repoRoot: Path) {
    private fun execute(vararg args: String): GitCommandResult {
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
        val res = execute("status", "--porcelain=v1")
        if (res.exitCode != 0) return emptyList()
        return res.stdout.lines().filter { it.isNotBlank() }
    }

    fun diff(paths: List<String>): String {
        paths.forEach { assertSafeRelativePath(it) }
        val cmd = mutableListOf("diff", "--no-color", "--")
        cmd.addAll(paths)
        return execute(*cmd.toTypedArray()).stdout
    }

    fun add(paths: List<String>): GitCommandResult {
        paths.forEach { assertSafeRelativePath(it) }
        val cmd = mutableListOf("add", "--")
        cmd.addAll(paths)
        return execute(*cmd.toTypedArray())
    }

    fun commit(message: String): GitCommandResult {
        require(message.isNotBlank()) { "Commit message cannot be blank" }
        return execute("commit", "-m", message)
    }
}
