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

class TypescriptNodeDriver(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val languageId = "typescript"

    override fun isAvailable(): Boolean {
        return NativeToolchainRegistry.locateBinary("tsc") != null || NativeToolchainRegistry.locateBinary("node") != null
    }

    override fun compile(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        if (!Files.isRegularFile(worktree.resolve("tsconfig.json"))) {
            return ToolchainResult(
                exitCode = 1,
                passed = false,
                stdout = "",
                stderr = "tsconfig.json not found",
                diagnostics = listOf(ToolchainDiagnostic("tsconfig.json", 0, 0, "MISSING_CONFIG", "tsconfig.json not found in worktree", DiagnosticSeverity.ERROR)),
                durationMs = 0
            )
        }

        val nodeBinary = NativeToolchainRegistry.locateBinary("node") ?: return ToolchainResult(1, false, "", "node not found", listOf(), 0)
        val tscBinary = NativeToolchainRegistry.locateBinary("tsc") ?: return ToolchainResult(1, false, "", "tsc not found", listOf(), 0)
        
        val command = listOf(nodeBinary.toString(), tscBinary.toString(), "--noEmit", "--pretty", "false")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        if (!Files.isRegularFile(worktree.resolve("package.json"))) {
            return ToolchainResult(
                exitCode = 1,
                passed = false,
                stdout = "",
                stderr = "package.json not found",
                diagnostics = listOf(ToolchainDiagnostic("package.json", 0, 0, "MISSING_CONFIG", "package.json not found in worktree", DiagnosticSeverity.ERROR)),
                durationMs = 0
            )
        }

        val nodeBinary = NativeToolchainRegistry.locateBinary("node") ?: return ToolchainResult(1, false, "", "node not found", listOf(), 0)
        val command = listOf(nodeBinary.toString(), "pnpm", "test", "--frozen-lockfile")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        if (!Files.isRegularFile(worktree.resolve("tsconfig.json"))) {
            return ToolchainResult(
                exitCode = 1,
                passed = false,
                stdout = "",
                stderr = "tsconfig.json not found",
                diagnostics = listOf(ToolchainDiagnostic("tsconfig.json", 0, 0, "MISSING_CONFIG", "tsconfig.json not found in worktree", DiagnosticSeverity.ERROR)),
                durationMs = 0
            )
        }

        val nodeBinary = NativeToolchainRegistry.locateBinary("node") ?: return ToolchainResult(1, false, "", "node not found", listOf(), 0)
        val tscBinary = NativeToolchainRegistry.locateBinary("tsc") ?: return ToolchainResult(1, false, "", "tsc not found", listOf(), 0)
        
        val command = listOf(nodeBinary.toString(), tscBinary.toString(), "--noEmit", "--pretty", "false")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        if (!Files.isRegularFile(worktree.resolve("package.json"))) {
            return ToolchainResult(
                exitCode = 1,
                passed = false,
                stdout = "",
                stderr = "package.json not found",
                diagnostics = listOf(ToolchainDiagnostic("package.json", 0, 0, "MISSING_CONFIG", "package.json not found in worktree", DiagnosticSeverity.ERROR)),
                durationMs = 0
            )
        }

        val nodeBinary = NativeToolchainRegistry.locateBinary("node") ?: return ToolchainResult(1, false, "", "node not found", listOf(), 0)
        val command = listOf(nodeBinary.toString(), "pnpm", "run", "build")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    private fun validateWorktree(worktree: Path) {
        if (!worktree.toFile().listFiles()?.any { it.isFile } == true) {
            throw IllegalArgumentException("Worktree must contain at least one source file")
        }
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseTscOutput(result.stdout + "\n" + result.stderr)
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

    private fun parseTscOutput(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val pattern = Pattern.compile("^(.+?)\\((\\d+),(\\d+)\\):\\s+(error|warning)\\s+(TS\\d+):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                val file = matcher.group(1)
                val lineNum = matcher.group(2).toInt()
                val colNum = matcher.group(3).toInt()
                val level = matcher.group(4)
                val code = matcher.group(5)
                val message = matcher.group(6)

                val severity = when (level) {
                    "error" -> DiagnosticSeverity.ERROR
                    "warning" -> DiagnosticSeverity.WARNING
                    else -> DiagnosticSeverity.INFO
                }

                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, code, message, severity))
            }
        }
        return diagnostics
    }
}