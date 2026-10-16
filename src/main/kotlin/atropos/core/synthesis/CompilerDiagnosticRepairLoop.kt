/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.synthesis

import atropos.core.provider.ProviderCascadeRouter
import atropos.core.toolchain.LanguageToolchainDriver
import atropos.core.toolchain.ToolchainResult
import java.nio.file.Files
import java.nio.file.Path

class CompilerDiagnosticRepairLoop(
    private val cascadeRouter: ProviderCascadeRouter,
    private val maxRetries: Int = 3
) {
    fun repair(
        worktree: Path,
        driver: LanguageToolchainDriver,
        initialFailure: ToolchainResult,
        basePrompt: String
    ): ToolchainResult {
        var currentResult = initialFailure

        for (attempt in 1..maxRetries) {
            if (currentResult.passed && currentResult.exitCode == 0) {
                return currentResult
            }

            val failingDiagnostics = currentResult.diagnostics
            val failingFile = failingDiagnostics.firstOrNull { it.file.isNotBlank() }?.file
                ?: currentResult.stderr.lines().firstOrNull { it.contains(":") }?.substringBefore(":")
                ?: break

            val targetPath = worktree.resolve(failingFile)
            val currentContent = if (Files.isRegularFile(targetPath)) Files.readString(targetPath) else ""

            val diagnosticSummary = failingDiagnostics.joinToString("\n") { diag ->
                "${diag.file}:${diag.line}:${diag.column}: [${diag.severity}] ${diag.message}"
            }.ifBlank { currentResult.stderr.take(500) }

            val repairPrompt = """[REPAIR ATTEMPT $attempt/$maxRetries]
Target File: $failingFile
Base Context: ${basePrompt.take(300)}

[COMPILER DIAGNOSTICS]
$diagnosticSummary

[CURRENT FAILING SOURCE]
$currentContent

[INSTRUCTION]
Return ONLY the corrected, complete file content for $failingFile. Do not explain. Do not wrap in markdown quotes if possible, or wrap cleanly."""

            val cascadeResult = cascadeRouter.completeWithCascade(
                requestedProvider = "default",
                prompt = repairPrompt,
                context = currentContent
            )

            var repairedCode = cascadeResult.response.trim()
            if (repairedCode.startsWith("```")) {
                repairedCode = repairedCode.substringAfter("\n").substringBeforeLast("```").trim()
            }

            if (repairedCode.isNotBlank()) {
                Files.createDirectories(targetPath.parent)
                Files.writeString(targetPath, repairedCode)
            }

            val compileResult = driver.compile(worktree)
            if (!compileResult.passed || compileResult.exitCode != 0) {
                currentResult = compileResult
                continue
            }

            val testResult = driver.test(worktree)
            currentResult = testResult
            if (testResult.passed && testResult.exitCode == 0) {
                return testResult
            }
        }

        return currentResult
    }
}
