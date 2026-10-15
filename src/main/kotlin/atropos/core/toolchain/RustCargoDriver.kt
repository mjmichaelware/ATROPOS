package atropos.core.toolchain

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import java.nio.file.Path
import java.util.regex.Pattern

class RustCargoDriver(
    override val binaryPath: Path,
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val language = ToolchainLanguage.RUST

    override fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val targetDir = workingDir.resolve("target/worktree_isolated")
        val command = listOf(binaryPath.toString(), "check", "--message-format=json", "--target-dir", targetDir.toString()) + extraArgs
        val result = processRunner.run(command, workingDir, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult {
        val targetDir = workingDir.resolve("target/worktree_isolated")
        val command = listOf(binaryPath.toString(), "test", "--no-fail-fast", "--message-format=json", "--target-dir", targetDir.toString(), "--color=never") + extraArgs
        val result = processRunner.run(command, workingDir, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun lint(workingDir: Path): ToolchainResult {
        val targetDir = workingDir.resolve("target/worktree_isolated")
        val command = listOf(binaryPath.toString(), "clippy", "--message-format=json", "--target-dir", targetDir.toString(), "-D", "warnings")
        val result = processRunner.run(command, workingDir, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult {
        val targetDir = workingDir.resolve("target/worktree_isolated")
        val command = listOf(binaryPath.toString(), "build", "--release", "--target-dir", targetDir.toString())
        val result = processRunner.run(command, workingDir, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseDiagnostics(result.stdout + "\n" + result.stderr)
        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR } || result.exitCode != 0
        return ToolchainResult(
            exitCode = if (hasErrors) 1 else result.exitCode ?: 1,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = diagnostics,
            durationMs = result.durationMillis
        )
    }

    private fun parseDiagnostics(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        val lines = output.lineSequence().toList()

        for (line in lines) {
            if (line.trim().isEmpty()) continue
            try {
                val json = com.google.gson.JsonParser.parseString(line).asJsonObject
                if (json.has("reason") && json.get("reason").asString == "compiler-message") {
                    val message = json.get("message").asJsonObject
                    val file = message.get("spans").asJsonArray.firstOrNull()?.asJsonObject?.get("file_name")?.asString
                    val lineNum = message.get("spans").asJsonArray.firstOrNull()?.asJsonObject?.get("line_start")?.asInt ?: 0
                    val colNum = message.get("spans").asJsonArray.firstOrNull()?.asJsonObject?.get("column_start")?.asInt ?: 0
                    val code = message.get("code")?.asJsonObject?.get("code")?.asString ?: ""
                    val msg = message.get("message").asString
                    val level = message.get("level").asString

                    val severity = when (level) {
                        "error" -> DiagnosticSeverity.ERROR
                        "warning" -> DiagnosticSeverity.WARNING
                        else -> DiagnosticSeverity.INFO
                    }

                    if (file != null) {
                        diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, code, msg, severity))
                    }
                }
            } catch (e: Exception) {
                // Skip malformed JSON lines
            }
        }
        return diagnostics
    }
}