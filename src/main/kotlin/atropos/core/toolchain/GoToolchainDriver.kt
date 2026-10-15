package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import java.nio.file.Path

class GoToolchainDriver(
    override val binaryPath: Path,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val language = ToolchainLanguage.GO

    override fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val env = mapOf(
            "GOCACHE" to workingDir.resolve(".gocache").toString(),
            "GOTMPDIR" to workingDir.resolve(".gotmp").toString()
        )
        val command = listOf(binaryPath.toString(), "build", "-v", "./...") + extraArgs
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000, environment = env)
        return parseResult(result)
    }

    override fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val env = mapOf(
            "GOCACHE" to workingDir.resolve(".gocache").toString(),
            "GOTMPDIR" to workingDir.resolve(".gotmp").toString()
        )
        val command = listOf(binaryPath.toString(), "test", "-race", "-v", "./...") + extraArgs
        val result = processRunner.run(command, workingDir, 300_000, 20_000_000, 4000, environment = env)
        return parseResult(result)
    }

    override fun lint(workingDir: Path): ToolchainResult {
        val env = mapOf(
            "GOCACHE" to workingDir.resolve(".gocache").toString(),
            "GOTMPDIR" to workingDir.resolve(".gotmp").toString()
        )
        val command = listOf(binaryPath.toString(), "vet", "./...")
        val result = processRunner.run(command, workingDir, 120_000, 20_000_000, 4000, environment = env)
        return parseResult(result)
    }

    override fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult {
        val env = mapOf(
            "GOCACHE" to workingDir.resolve(".gocache").toString(),
            "GOTMPDIR" to workingDir.resolve(".gotmp").toString()
        )
        val command = listOf(binaryPath.toString(), "build", "-v", "-o", outputDir.resolve("app").toString(), "./...")
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000, environment = env)
        return parseResult(result)
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseGoErrors(result.stderr)
        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR } || result.exitCode != 0
        return ToolchainResult(
            exitCode = if (hasErrors) 1 else result.exitCode ?: 1,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseGoErrors(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val pattern = java.util.regex.Pattern.compile("^(.+?):(\\d+):(\\d+):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                val file = matcher.group(1)
                val lineNum = matcher.group(2).toInt()
                val colNum = matcher.group(3).toInt()
                val message = matcher.group(4)

                val severity = if (message.contains("error") || message.contains("ERROR")) {
                    DiagnosticSeverity.ERROR
                } else {
                    DiagnosticSeverity.WARNING
                }

                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, "GO", message, severity))
            }
        }
        return diagnostics
    }
}