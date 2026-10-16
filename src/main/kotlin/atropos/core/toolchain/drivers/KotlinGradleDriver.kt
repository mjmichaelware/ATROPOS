/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain.drivers

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.toolchain.NativeToolchainRegistry
import atropos.core.toolchain.ToolchainDiagnostic
import atropos.core.toolchain.ToolchainResult
import atropos.core.verification.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

class KotlinGradleDriver(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val languageId = "kotlin"

    override fun isAvailable(): Boolean {
        return NativeToolchainRegistry.locateBinary("gradle") != null ||
            NativeToolchainRegistry.locateBinary("kotlinc") != null
    }

    override fun compile(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("compileKotlin"))
        val result = runner.run(command, worktree, 600_000, 20_000_000, 4000)
        return parseGradleResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("test"))
        val result = runner.run(command, worktree, 600_000, 20_000_000, 4000)
        return parseGradleResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("ktlintCheck"))
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000)
        return parseGradleResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val command = buildGradleCommand(worktree, listOf("assemble"))
        val result = runner.run(command, worktree, 600_000, 20_000_000, 4000)
        return parseGradleResult(result)
    }

    private fun buildGradleCommand(worktree: Path, tasks: List<String>): List<String> {
        val gradlew = worktree.resolve("gradlew")
        val executable = if (Files.isExecutable(gradlew)) {
            gradlew.toAbsolutePath().toString()
        } else {
            val systemGradle = NativeToolchainRegistry.locateBinary("gradle")
                ?: throw IllegalStateException("Neither ./gradlew nor system gradle found")
            systemGradle.toString()
        }
        return listOf(executable) + tasks + listOf("--no-daemon", "--console=plain", "--stacktrace")
    }

    private fun validateWorktree(worktree: Path) {
        val files = worktree.toFile().listFiles()
        if (files == null || files.none { it.isFile }) {
            throw IllegalArgumentException("Worktree must contain at least one source file")
        }
    }

    private fun parseGradleResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseGradleDiagnostics(result.stdout + "\n" + result.stderr)
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

    private fun parseGradleDiagnostics(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        // kotlinc format: e: file: (line, col): message   or   w: file: (line, col): message
        val pattern = Pattern.compile("^([ew]):\\s+(.+?):\\s+\\((\\d+),\\s*(\\d+)\\):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                val level = matcher.group(1)
                val file = matcher.group(2)
                val lineNum = matcher.group(3).toInt()
                val colNum = matcher.group(4).toInt()
                val message = matcher.group(5)
                val severity = if (level == "e") DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING
                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, null, message, severity))
            }
        }
        return diagnostics
    }
}
