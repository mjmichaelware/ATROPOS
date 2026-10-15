package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import java.nio.file.Files
import java.nio.file.Path

class KotlinGradleDriver(
    override val binaryPath: Path,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val language = ToolchainLanguage.KOTLIN

    override fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val command = buildGradleCommand(workingDir, "compileKotlin", extraArgs)
        val result = processRunner.run(command, workingDir, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val command = buildGradleCommand(workingDir, "test", extraArgs)
        val result = processRunner.run(command, workingDir, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun lint(workingDir: Path): ToolchainResult {
        val command = buildGradleCommand(workingDir, "detekt", extraArgs)
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult {
        val command = buildGradleCommand(workingDir, "jar", extraArgs)
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    private fun buildGradleCommand(workingDir: Path, task: String, extraArgs: List<String>): List<String> {
        val gradleWrapper = workingDir.resolve("gradlew")
        if (Files.isExecutable(gradleWrapper)) {
            return listOf(gradleWrapper.toString(), task, "--no-daemon", "--offline", "-Dorg.gradle.project.cachedir=${workingDir}/.gradle") + extraArgs
        } else {
            return listOf("kotlinc", "-version") + extraArgs
        }
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseGradleDiagnostics(result.stdout + "\n" + result.stderr)
        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR } || result.exitCode != 0
        return ToolchainResult(
            exitCode = if (hasErrors) 1 else result.exitCode ?: 1,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseGradleDiagnostics(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val pattern = java.util.regex.Pattern.compile("^e:\\s+(.+?):\\s+\\((\\d+),\\s*(\\d+)\\):\\s+(.+)$")

        for (line in output.lineSequence()) {
            val matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                val file = matcher.group(1)
                val lineNum = matcher.group(2).toInt()
                val colNum = matcher.group(3).toInt()
                val message = matcher.group(4)

                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, "KOTLIN", message, DiagnosticSeverity.ERROR))
            }
        }
        return diagnostics
    }
}