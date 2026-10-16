/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain.drivers

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.toolchain.NativeToolchainRegistry
import atropos.core.toolchain.ToolchainDiagnostic
import atropos.core.toolchain.ToolchainResult
import atropos.core.verification.DiagnosticSeverity
import com.google.gson.JsonParser
import java.nio.file.Path
import java.util.regex.Pattern

class RustCargoDriver(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val languageId = "rust"

    override fun isAvailable(): Boolean {
        return NativeToolchainRegistry.locateBinary("cargo") != null
    }

    override fun compile(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "check", "--message-format=json", "--target-dir", targetDir.toString())
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "test", "--no-fail-fast", "--message-format=json", "--target-dir", targetDir.toString(), "--color=never")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "clippy", "--message-format=json", "--target-dir", targetDir.toString(), "-D", "warnings")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "build", "--release", "--target-dir", targetDir.toString())
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000)
        return parseResult(result)
    }

    private fun validateWorktree(worktree: Path) {
        if (!worktree.toFile().listFiles()?.any { it.isFile } == true) {
            throw IllegalArgumentException("Worktree must contain at least one source file")
        }
    }

    private fun parseResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseDiagnostics(result.stdout + "\n" + result.stderr)
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

    private fun parseDiagnostics(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()

        for (line in output.lineSequence()) {
            if (line.trim().isEmpty()) continue
            try {
                val json = JsonParser.parseString(line).asJsonObject
                if (json.has("reason") && json.get("reason").asString == "compiler-message") {
                    val message = json.get("message").asJsonObject
                    val spans = message.get("spans").asJsonArray
                    if (spans.size > 0) {
                        val span = spans[0].asJsonObject
                        val file = span.get("file_name")?.asString
                        val lineNum = span.get("line_start")?.asInt ?: 0
                        val colNum = span.get("column_start")?.asInt ?: 0
                        val code = message.get("code")?.asJsonObject?.get("code")?.asString
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
                }
            } catch (e: Exception) {
                // Skip malformed JSON lines
            }
        }
        return diagnostics
    }
}