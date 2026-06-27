/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verifier

import atropos.core.verification.*

class ProbabilisticImmunityEngine {
    private val standard = Regex(
        """^(.*?\.kt):(\d+):(\d+):\s*(error|warning|info):?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val prefixed = Regex(
        """^([ewi]):\s*(?:file://)?(.*?\.kt):(\d+):(\d+)\s+(.*)$""",
        RegexOption.IGNORE_CASE
    )

    fun analyze(execution: ProcessExecution): DiagnosticReport {
        val diagnostics = sequenceOf(execution.stdout.text, execution.stderr.text)
            .flatMap { it.lineSequence() }
            .mapNotNull(::parseLine)
            .toMutableList()

        if (
            diagnostics.isEmpty() &&
            (execution.exitCode != 0 || execution.timedOut || execution.launchError != null)
        ) {
            val message = execution.launchError
                ?: execution.stderr.text.lineSequence()
                    .lastOrNull { it.isNotBlank() }
                ?: if (execution.timedOut) "Verification timed out"
                else "Verification process failed"

            diagnostics += CompilerDiagnostic(
                path = null,
                line = null,
                column = null,
                severity = DiagnosticSeverity.ERROR,
                message = message.take(500),
                raw = message.take(500)
            )
        }

        val classification = when {
            execution.timedOut -> FailureClassification.TIMEOUT
            execution.launchError != null -> FailureClassification.TOOL_UNAVAILABLE
            execution.exitCode == 0 -> FailureClassification.SUCCESS
            diagnostics.any { it.severity == DiagnosticSeverity.ERROR } ->
                FailureClassification.COMPILATION_ERROR
            else -> FailureClassification.PROCESS_FAILURE
        }

        val recommendations = diagnostics.asSequence()
            .mapNotNull(::recommend)
            .distinct()
            .take(12)
            .toList()

        val errors = diagnostics.count { it.severity == DiagnosticSeverity.ERROR }
        val warnings = diagnostics.count { it.severity == DiagnosticSeverity.WARNING }
        val risk = when (classification) {
            FailureClassification.SUCCESS -> (warnings * 2).coerceAtMost(20)
            FailureClassification.COMPILATION_ERROR -> (40 + errors * 8).coerceAtMost(100)
            FailureClassification.TIMEOUT -> 90
            FailureClassification.TOOL_UNAVAILABLE -> 100
            FailureClassification.PROCESS_FAILURE -> 80
        }

        return DiagnosticReport(classification, diagnostics, recommendations, risk)
    }

    fun parseLine(raw: String): CompilerDiagnostic? {
        val line = raw.trim()
        if (line.isEmpty()) return null

        standard.matchEntire(line)?.let { match ->
            return CompilerDiagnostic(
                cleanPath(match.groupValues[1]),
                match.groupValues[2].toIntOrNull(),
                match.groupValues[3].toIntOrNull(),
                severity(match.groupValues[4]),
                match.groupValues[5].trim(),
                raw
            )
        }

        prefixed.matchEntire(line)?.let { match ->
            return CompilerDiagnostic(
                cleanPath(match.groupValues[2]),
                match.groupValues[3].toIntOrNull(),
                match.groupValues[4].toIntOrNull(),
                severity(match.groupValues[1]),
                match.groupValues[5].trim(),
                raw
            )
        }

        return null
    }

    private fun cleanPath(path: String): String =
        path.removePrefix("file://").trim()

    private fun severity(value: String): DiagnosticSeverity =
        when (value.lowercase()) {
            "e", "error" -> DiagnosticSeverity.ERROR
            "w", "warning" -> DiagnosticSeverity.WARNING
            else -> DiagnosticSeverity.INFO
        }

    private fun recommend(diagnostic: CompilerDiagnostic): String? {
        val message = diagnostic.message.lowercase()
        return when {
            "unresolved reference" in message ->
                "Check the referenced symbol, package, import, and compile dependencies."
            "type mismatch" in message || "argument type mismatch" in message ->
                "Align the declared and supplied Kotlin types at the reported location."
            "expecting" in message ->
                "Inspect syntax delimiters near the reported line and column."
            "overload resolution ambiguity" in message ->
                "Disambiguate the call using explicit types or a qualified symbol."
            "outofmemory" in message || "out of memory" in message ->
                "Reduce the compile slice or adjust the bounded JVM memory configuration."
            else -> null
        }
    }
}
