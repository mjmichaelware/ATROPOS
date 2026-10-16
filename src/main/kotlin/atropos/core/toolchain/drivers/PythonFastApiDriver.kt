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

class PythonFastApiDriver(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) : LanguageToolchainDriver {
    override val languageId = "python"

    override fun isAvailable(): Boolean {
        return NativeToolchainRegistry.locateBinary("python3") != null ||
            NativeToolchainRegistry.locateBinary("python") != null
    }

    override fun compile(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val python = resolvePython(worktree)
        val command = listOf(python, "-m", "mypy", "--strict", "--show-column-numbers", ".")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000, buildEnv(worktree))
        return parseMypyResult(result)
    }

    override fun test(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val python = resolvePython(worktree)
        val command = listOf(python, "-m", "pytest", "-v", "--tb=short")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000, buildEnv(worktree))
        return parseGenericResult(result)
    }

    override fun lint(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val python = resolvePython(worktree)
        val command = listOf(python, "-m", "ruff", "check", "--output-format=json", ".")
        val result = runner.run(command, worktree, 180_000, 20_000_000, 4000, buildEnv(worktree))
        return parseRuffResult(result)
    }

    override fun packageArtifact(worktree: Path): ToolchainResult {
        validateWorktree(worktree)
        val mainPy = worktree.resolve("main.py")
        if (!Files.isRegularFile(mainPy)) {
            return ToolchainResult(
                exitCode = 1,
                passed = false,
                stdout = "",
                stderr = "main.py not found in worktree",
                diagnostics = listOf(
                    ToolchainDiagnostic(
                        "main.py", 0, 0, "MISSING_ENTRY",
                        "main.py not found in worktree root", DiagnosticSeverity.ERROR
                    )
                ),
                durationMs = 0
            )
        }
        val python = resolvePython(worktree)
        val command = listOf(python, "-m", "build")
        val result = runner.run(command, worktree, 300_000, 20_000_000, 4000, buildEnv(worktree))
        return parseGenericResult(result)
    }

    private fun resolvePython(worktree: Path): String {
        val venvPython = worktree.resolve(".venv/bin/python3")
        if (Files.isExecutable(venvPython)) return venvPython.toAbsolutePath().toString()
        val venvPython2 = worktree.resolve(".venv/bin/python")
        if (Files.isExecutable(venvPython2)) return venvPython2.toAbsolutePath().toString()
        val sys3 = NativeToolchainRegistry.locateBinary("python3")
        if (sys3 != null) return sys3.toString()
        val sys = NativeToolchainRegistry.locateBinary("python")
        if (sys != null) return sys.toString()
        throw IllegalStateException("No Python interpreter found on host")
    }

    private fun buildEnv(worktree: Path): Map<String, String> {
        return mapOf(
            "PYTHONDONTWRITEBYTECODE" to "1",
            "PYTHONUNBUFFERED" to "1"
        )
    }

    private fun validateWorktree(worktree: Path) {
        val files = worktree.toFile().listFiles()
        if (files == null || files.none { it.isFile }) {
            throw IllegalArgumentException("Worktree must contain at least one source file")
        }
    }

    private fun parseMypyResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseMypyOutput(result.stdout + "\n" + result.stderr)
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

    private fun parseRuffResult(result: BoundedProcessResult): ToolchainResult {
        val diagnostics = parseRuffOutput(result.stdout + "\n" + result.stderr)
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

    private fun parseGenericResult(result: BoundedProcessResult): ToolchainResult {
        return ToolchainResult(
            exitCode = result.exitCode ?: 1,
            passed = result.exitCode == 0,
            stdout = result.stdout,
            stderr = result.stderr,
            diagnostics = emptyList(),
            durationMs = result.durationMillis
        )
    }

    private fun parseMypyOutput(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        // format: file:line:col: level: message
        val pattern = Pattern.compile("^(.+?):(\\d+):(\\d+):\\s+(error|warning|note):\\s+(.+)$")
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = pattern.matcher(trimmed)
            if (matcher.matches()) {
                val file = matcher.group(1)
                val lineNum = matcher.group(2).toInt()
                val colNum = matcher.group(3).toInt()
                val level = matcher.group(4)
                val message = matcher.group(5)
                val severity = when (level) {
                    "error" -> DiagnosticSeverity.ERROR
                    "warning" -> DiagnosticSeverity.WARNING
                    else -> DiagnosticSeverity.INFO
                }
                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, null, message, severity))
            }
        }
        return diagnostics
    }

    private fun parseRuffOutput(output: String): List<ToolchainDiagnostic> {
        val diagnostics = mutableListOf<ToolchainDiagnostic>()
        try {
            val trimmed = output.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("[")) return diagnostics
            val arrayNode = com.google.gson.JsonParser.parseString(trimmed).asJsonArray
            for (element in arrayNode) {
                val obj = element.asJsonObject
                val file = obj.get("filename")?.asString ?: continue
                val location = obj.get("location")?.asJsonObject
                val lineNum = location?.get("row")?.asInt ?: 0
                val colNum = location?.get("column")?.asInt ?: 0
                val code = obj.get("code")?.asString
                val message = obj.get("message")?.asString ?: ""
                diagnostics.add(ToolchainDiagnostic(file, lineNum, colNum, code, message, DiagnosticSeverity.ERROR))
            }
        } catch (_: Exception) {
            // Non-JSON output — not fatal, diagnostics remain empty
        }
        return diagnostics
    }
}
