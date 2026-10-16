/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import atropos.core.verification.DiagnosticSeverity
import java.nio.file.Path

data class ToolchainDiagnostic(
    val file: String,
    val line: Int,
    val column: Int,
    val code: String?,
    val message: String,
    val severity: DiagnosticSeverity,
    val errorCode: String? = code,
    val trace: String? = null
)

data class ToolchainResult(
    val exitCode: Int,
    val passed: Boolean,
    val stdout: String,
    val stderr: String,
    val diagnostics: List<ToolchainDiagnostic> = emptyList(),
    val durationMs: Long = 0L,
    val converged: Boolean = passed && exitCode == 0
) {
    companion object {
        fun converged(stdout: String = "", durationMs: Long = 0L): ToolchainResult =
            ToolchainResult(
                exitCode = 0,
                passed = true,
                stdout = stdout,
                stderr = "",
                diagnostics = emptyList(),
                durationMs = durationMs,
                converged = true
            )
    }
}

interface LanguageToolchainDriver {
    val languageId: String
    fun isAvailable(): Boolean
    fun compile(worktree: Path): ToolchainResult
    fun test(worktree: Path): ToolchainResult
    fun lint(worktree: Path): ToolchainResult
    fun packageArtifact(worktree: Path): ToolchainResult
}