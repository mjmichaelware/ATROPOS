package atropos.core.repair

import java.time.Instant

/**
 * TerminalErrorFeedbackLoopCorrector
 *
 * Implements ATOM AI14: TERMINAL ERROR FEEDBACK LOOP AUTONOMOUS CORRECTOR
 * Captures non-zero terminal exit codes, compiler diagnostics, stack traces, and test failures.
 * Formulates structured error contexts and synthesizes prompt directives to trigger autonomous
 * self-healing loops without operator intervention.
 */
data class TerminalExecutionFailure(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val workingDirectory: String,
    val timestamp: Instant = Instant.now()
)

data class CorrectiveActionProposal(
    val failureId: String,
    val rootCauseSummary: String,
    val suspectedFiles: List<String>,
    val promptDirective: String,
    val suggestedPatches: List<String> = emptyList()
)

class TerminalErrorFeedbackLoopCorrector(
    private val maxFeedbackCycles: Int = 5
) {
    fun analyzeFailure(failure: TerminalExecutionFailure): CorrectiveActionProposal {
        val rootCause = diagnoseRootCause(failure.stderr, failure.stdout)
        val files = extractSuspectedFiles(failure.stderr, failure.stdout)

        val directive = buildString {
            append("Terminal command failed with exit code ${failure.exitCode}: `${failure.command}`\n")
            append("Diagnosed Root Cause: $rootCause\n")
            if (files.isNotEmpty()) {
                append("Suspected Files:\n")
                files.forEach { append("  - $it\n") }
            }
            append("Instruction: Apply minimal atomic AST modification to eliminate this failure while preserving test invariants.\n")
        }

        return CorrectiveActionProposal(
            failureId = java.util.UUID.randomUUID().toString(),
            rootCauseSummary = rootCause,
            suspectedFiles = files,
            promptDirective = directive
        )
    }

    private fun diagnoseRootCause(stderr: String, stdout: String): String {
        val combined = "$stderr\n$stdout"
        return when {
            combined.contains("Unresolved reference") -> "Unresolved symbol or missing import declaration in Kotlin source."
            combined.contains("Syntax error") -> "Syntax violation or unclosed delimiter."
            combined.contains("Cannot find module") -> "Missing npm package or unresolved TypeScript module path."
            combined.contains("Type error") || combined.contains("Type mismatch") -> "Static type mismatch between expected and provided types."
            combined.contains("NullPointerException") -> "Unexpected null reference encountered during execution."
            else -> "Runtime or compilation failure; inspect stdout/stderr logs."
        }
    }

    private fun extractSuspectedFiles(stderr: String, stdout: String): List<String> {
        val regex = Regex("([a-zA-Z0-9_./-]+\\.(?:kt|java|ts|tsx|js|json|py|go|rs)):(\\d+)")
        val combined = "$stderr\n$stdout"
        return regex.findAll(combined)
            .map { it.groupValues[1] }
            .distinct()
            .take(5)
            .toList()
    }

    companion object {
        private val defaultInstance = TerminalErrorFeedbackLoopCorrector()
        fun defaultCorrector(): TerminalErrorFeedbackLoopCorrector = defaultInstance
    }
}
