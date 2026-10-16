/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain.drivers

import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.BoundedProcessResult
import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.toolchain.NativeToolchainRegistry
import atropos.core.toolchain.ToolchainDiagnostic
import atropos.core.toolchain.ToolchainResult
import atropos.core.verification.DiagnosticSeverity
import org.json.JSONObject
import java.nio.file.Path

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
        val result = runner.run(command, worktree, 300_000, 256 * 1024, 4000)
        return parseResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "test", "--no-fail-fast", "--message-format=json", "--target-dir", targetDir.toString(), "--color=never")
        val result = runner.run(command, worktree, 300_000, 256 * 1024, 4000)
        return parseResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "clippy", "--message-format=json", "--target-dir", targetDir.toString(), "-D", "warnings")
        val result = runner.run(command, worktree, 180_000, 256 * 1024, 4000)
        return parseResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val targetDir = worktree.resolve("target/worktree_isolated")
        val command = listOf("cargo", "build", "--release", "--target-dir", targetDir.toString())
        val result = runner.run(command, worktree, 300_000, 256 * 1024, 4000)
        return parseResult(result)
    }

    private fun validateWorktree(worktree: Path) {
        val files = worktree.toFile().listFiles()
        if (files == null || files.none { it.isFile }) {
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
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                val json = JSONObject(trimmed)
                if (json.optString("reason") == "compiler-message") {
                    val message = json.getJSONObject("message")
                    val spans = message.optJSONArray("spans")
                    if (spans != null && spans.length() > 0) {
                        val span = spans.getJSONObject(0)
                        val file = span.optString("file_name", "")
                        val lineNum = span.optInt("line_start", 0)
                        val colNum = span.optInt("column_start", 0)
                        val code = message.optJSONObject("code")?.optString("code")
                        val msg = message.optString("message", "")
                        val level = message.optString("level", "")

                        val severity = when (level) {
                            "error" -> DiagnosticSeverity.ERROR
                            "warning" -> DiagnosticSeverity.WARNING
                            else -> DiagnosticSeverity.INFO
                        }

                        if (file.isNotEmpty()) {
                            diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, code, msg, severity))
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip malformed or non-JSON lines
            }
        }
        return diagnostics
    }
}