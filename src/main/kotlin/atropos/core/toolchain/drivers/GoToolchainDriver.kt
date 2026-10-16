/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain.drivers

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.toolchain.NativeToolchainRegistry
import atropos.core.toolchain.ToolchainDiagnostic
import atropos.core.toolchain.ToolchainResult
import atropos.core.verification.DiagnosticSeverity
import java.nio.file.Path
import java.util.regex.Pattern

class GoToolchainDriver(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val languageId = "go"

    override fun isAvailable(): Boolean {
        return NativeToolchainRegistry.locateBinary("go") != null
    }

    override fun compile(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = listOf("go", "build", "./...")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000, buildEnv(worktree))
        return parseResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = listOf("go", "test", "-v", "-race", "-count=1", "./...")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000, buildEnv(worktree))
        return parseResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = listOf("go", "vet", "./...")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000, buildEnv(worktree))
        return parseResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val distDir = worktree.resolve("dist")
        distDir.toFile().mkdirs()
        val command = listOf("go", "build", "-o", distDir.resolve("app").toString(), "./...")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000, buildEnv(worktree))
        return parseResult(result)
    }

    private fun buildEnv(worktree: Path): Map<String, String> {
        return mapOf(
            "GOPATH" to worktree.resolve(".gopath").toAbsolutePath().toString(),
            "GOCACHE" to worktree.resolve(".gocache").toAbsolutePath().toString(),
            "GOMAXPROCS" to "4",
            "CGO_ENABLED" to "0"
        )
    }

    private fun validateWorktree(worktree: Path) {
        val files = worktree.toFile().listFiles()
        if (files == null || files.none { it.isFile }) {
            throw IllegalArgumentException("Worktree must contain at least one source file")
        }
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseGoErrors(result.stdout + "\n" + result.stderr)
        val passed = diagnostics.none { it.severity == DiagnosticSeverity.ERROR } && result.exitCode == 0
        return ToolchainResult(
            exitCode = result.exitCode ?: 1,
            passed = passed,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseGoErrors(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val pattern = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                val file = matcher.group(1)
                val lineNum = matcher.group(2).toInt()
                val colNum = matcher.group(3).toInt()
                val message = matcher.group(4)

                val severity = when {
                    message.startsWith("warning:") -> DiagnosticSeverity.WARNING
                    message.startsWith("note:") -> DiagnosticSeverity.INFO
                    else -> DiagnosticSeverity.ERROR
                }

                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, null, message, severity))
            }
        }
        return diagnostics
    }
}
