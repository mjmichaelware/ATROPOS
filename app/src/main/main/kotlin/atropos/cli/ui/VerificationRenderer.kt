/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.verification.*
import atropos.core.security.RedactionFilter

class VerificationRenderer(
    private val theme: TerminalTheme,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun render(result: VerificationResult, width: Int): List<String> {
        val execution = result.execution
        val scope = result.request.scope.name.lowercase()
        val heading = when {
            result.successful ->
                theme.success("✓ verification passed")

            execution.timedOut ->
                theme.error("✗ verification timed out")

            else ->
                theme.error("✗ verification failed")
        }

        val metadata = listOf(
            scope,
            "${execution.durationMillis} ms",
            "reward ${formatReward(result.reward)}",
            result.report.classification.name.lowercase()
        ).joinToString(" · ")

        val output = mutableListOf(
            "$heading ${theme.metadata("· $metadata")}"
        )

        result.report.diagnostics
            .take(12)
            .forEach { diagnostic ->
                output += renderDiagnostic(diagnostic, width)
            }

        result.report.recommendations
            .take(6)
            .forEach { recommendation ->
                output += theme.metadata("  recommendation: ") +
                    TerminalText.ellipsize(
                        redactionFilter.redact(recommendation),
                        (width - 18).coerceAtLeast(18)
                    )
            }

        if (
            execution.stdout.truncated ||
            execution.stderr.truncated
        ) {
            output += theme.warning(
                "  output truncated at configured bounds"
            )
        }

        result.persistenceError?.let { failure ->
            output += theme.error(
                "  reward persistence: " +
                    TerminalText.ellipsize(
                        redactionFilter.redact(failure),
                        (width - 22).coerceAtLeast(18)
                    )
            )
        }

        return output
    }

    private fun renderDiagnostic(
        diagnostic: CompilerDiagnostic,
        width: Int
    ): String {
        val location = listOfNotNull(
            diagnostic.path,
            diagnostic.line?.toString(),
            diagnostic.column?.toString()
        ).joinToString(":")

        val severity = when (diagnostic.severity) {
            DiagnosticSeverity.ERROR ->
                theme.error("error")

            DiagnosticSeverity.WARNING ->
                theme.warning("warning")

            DiagnosticSeverity.INFO ->
                theme.metadata("info")
        }

        val prefix = if (location.isEmpty()) {
            "  $severity: "
        } else {
            "  ${theme.path(location)} $severity: "
        }

        return prefix + TerminalText.ellipsize(
            redactionFilter.redact(diagnostic.message),
            (width - TerminalText.cellWidth(prefix))
                .coerceAtLeast(16)
        )
    }

    private fun formatReward(reward: Double): String =
        if (reward > 0.0) "+${reward}" else reward.toString()
}
