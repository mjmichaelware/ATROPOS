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
    val severity: DiagnosticSeverity
)

data class ToolchainResult(
    val exitCode: Int,
    val passed: Boolean,
    val stdout: String,
    val stderr: String,
    val diagnostics: List<ToolchainDiagnostic>,
    val durationMs: Long
)

interface LanguageToolchainDriver {
    val languageId: String
    fun isAvailable(): Boolean
    fun compile(worktree: Path): ToolchainResult
    fun test(worktree: Path): ToolchainResult
    fun lint(worktree: Path): ToolchainResult
    fun packageArtifact(worktree: Path): ToolchainResult
}