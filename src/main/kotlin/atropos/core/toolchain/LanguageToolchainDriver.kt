package atropos.core.toolchain

import java.nio.file.Path

data class ToolchainDiagnostic(
    val file: String,
    val line: Int,
    val column: Int,
    val code: String,
    val message: String,
    val severity: DiagnosticSeverity
)

data class ToolchainResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val diagnostics: List<ToolchainDiagnostic>,
    val durationMs: Long
)

enum class DiagnosticSeverity {
    ERROR, WARNING, INFO
}

enum class ToolchainLanguage {
    KOTLIN, RUST, TYPESCRIPT, GO, PYTHON, ANDROID
}

interface LanguageToolchainDriver {
    val language: ToolchainLanguage
    val binaryPath: Path
    fun compile(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult
    fun test(workingDir: Path, extraArgs: List<String> = emptyList()): ToolchainResult
    fun lint(workingDir: Path): ToolchainResult
    fun packageArtifact(workingDir: Path, outputDir: Path): ToolchainResult
}