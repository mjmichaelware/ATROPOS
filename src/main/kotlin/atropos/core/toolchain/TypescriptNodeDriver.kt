package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import java.nio.file.Files
import java.nio.file.Path

class TypescriptNodeDriver(
    override val binaryPath: Path,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val language = ToolchainLanguage.TYPESCRIPT

    private val nodeBinary: Path
        get() = binaryPath.parent.resolve("node")

    override fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        if (!Files.isRegularFile(workingDir.resolve("tsconfig.json"))) {
            return ToolchainResult(1, "", "tsconfig.json not found", listOf(
                ToolchainDiagnostic("tsconfig.json", 0, 0, "MISSING_CONFIG", "tsconfig.json not found in worktree", DiagnosticSeverity.ERROR)
            ), 0)
        }
        if (!Files.isRegularFile(workingDir.resolve("package.json"))) {
            return ToolchainResult(1, "", "package.json not found", listOf(
                ToolchainDiagnostic("package.json", 0, 0, "MISSING_CONFIG", "package.json not found in worktree", DiagnosticSeverity.ERROR)
            ), 0)
        }

        val command = listOf(nodeBinary.toString(), binaryPath.toString(), "--noEmit", "--pretty", "false") + extraArgs
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        if (!Files.isRegularFile(workingDir.resolve("package.json"))) {
            return ToolchainResult(1, "", "package.json not found", listOf(
                ToolchainDiagnostic("package.json", 0, 0, "MISSING_CONFIG", "package.json not found in worktree", DiagnosticSeverity.ERROR)
            ), 0)
        }

        val command = listOf(nodeBinary.toString(), "pnpm", "test", "--frozen-lockfile") + extraArgs
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun lint(workingDir: Path): ToolchainResult {
        if (!Files.isRegularFile(workingDir.resolve("tsconfig.json"))) {
            return ToolchainResult(1, "", "tsconfig.json not found", listOf(
                ToolchainDiagnostic("tsconfig.json", 0, 0, "MISSING_CONFIG", "tsconfig.json not found in worktree", DiagnosticSeverity.ERROR)
            ), 0)
        }

        val command = listOf(nodeBinary.toString(), binaryPath.toString(), "--noEmit", "--pretty", "false")
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult {
        if (!Files.isRegularFile(workingDir.resolve("package.json"))) {
            return ToolchainResult(1, "", "package.json not found", listOf(
                ToolchainDiagnostic("package.json", 0, 0, "MISSING_CONFIG", "package.json not found in worktree", DiagnosticSeverity.ERROR)
            ), 0)
        }

        val command = listOf(nodeBinary.toString(), "pnpm", "run", "build")
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseTscOutput(result.stdout + "\n" + result.stderr)
        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR } || result.exitCode != 0
        return ToolchainResult(
            exitCode = if (hasErrors) 1 else result.exitCode ?: 1,
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