package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import java.nio.file.Files
import java.nio.file.Path

class PythonFastApiDriver(
    override val binaryPath: Path,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val language = ToolchainLanguage.PYTHON

    private fun getVenvPython(workingDir: Path): Path {
        return workingDir.resolve(".venv/bin/python")
    }

    private fun ensureVenv(workingDir: Path): ToolchainResult? {
        val venvDir = workingDir.resolve(".venv")
        if (!Files.isDirectory(venvDir)) {
            val command = listOf(binaryPath.toString(), "-m", "venv", venvDir.toString())
            val result = processRunner.run(command, workingDir, 60_000, 20_000_000, 4000)
            if (result.exitCode != 0) {
                return ToolchainResult(
                    exitCode = result.exitCode ?: 1,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    diagnostics = listOf(ToolchainDiagnostic("venv", 0, 0, "VENV_CREATE_FAILED", "Failed to create virtual environment", DiagnosticSeverity.ERROR)),
                    durationMs = result.durationMillis
                )
            }
        }
        return null
    }

    override fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val venvError = ensureVenv(workingDir)
        if (venvError != null) return venvError

        val python = getVenvPython(workingDir)
        val command = listOf(python.toString(), "-m", "mypy", "--strict", "--show-column-numbers", ".") + extraArgs
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val venvError = ensureVenv(workingDir)
        if (venvError != null) return venvError

        val python = getVenvPython(workingDir)
        val command = listOf(python.toString(), "-m", "pytest", "-q") + extraArgs
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun lint(workingDir: Path): ToolchainResult {
        val venvError = ensureVenv(workingDir)
        if (venvError != null) return venvError

        val python = getVenvPython(workingDir)
        val command = listOf(python.toString(), "-m", "ruff", "check")
        val result = processRunner.run(command, workingDir, 120_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult {
        val venvError = ensureVenv(workingDir)
        if (venvError != null) return venvError

        val python = getVenvPython(workingDir)
        val command = listOf(python.toString(), "-m", "pip", "wheel", ".", "-w", outputDir.toString())
        val result = processRunner.run(command, workingDir, 120_000, 20_000_000, 4000)
        return parseResult(result)
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseMypyOutput(result.stdout + "\n" + result.stderr)
        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR } || result.exitCode != 0
        return ToolchainResult(
            exitCode = if (hasErrors) 1 else result.exitCode ?: 1,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseMypyOutput(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val pattern = java.util.regex.Pattern.compile("^(.+?):(\\d+):(\\d+):\\s+(error|warning|note):\\s+\\[(.+?)\\]\\s+(.+)$")

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