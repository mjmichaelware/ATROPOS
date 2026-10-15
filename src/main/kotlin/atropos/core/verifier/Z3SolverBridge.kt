package atropos.core.verifier

import atropos.core.policy.BoundedProcessRunner
import java.nio.file.Path

sealed interface SmtResult {
    data object Sat : SmtResult
    data object Unsat : SmtResult
    data class Unknown(val reason: String) : SmtResult
    data class Model(val assignments: Map<String, String>) : SmtResult
}

class Z3SolverBridge(
    private val z3BinaryPath: Path = Path.of("z3"),
    private val timeoutMs: Long = 5000
) {
    private val processRunner = BoundedProcessRunner()

    fun checkSat(smtLibQuery: String): SmtResult {
        val tempFile = java.io.File.createTempFile("z3-query-", ".smt2")
        tempFile.deleteOnExit()
        java.nio.file.Files.writeString(tempFile.toPath(), smtLibQuery)

        val command = listOf(z3BinaryPath.toString(), "-smt2", "-in", "-t:$timeoutMs")
        val result = processRunner.run(
            command,
            tempFile.parentFile.toPath(),
            timeoutMs,
            1_000_000,
            1000,
            standardInput = smtLibQuery.toByteArray()
        )

        return when {
            result.exitCode == null || result.timedOut -> SmtResult.Unknown("Z3 timeout or launch failure")
            result.stdout.contains("unsat") -> SmtResult.Unsat
            result.stdout.contains("sat") -> {
                val model = extractModel(result.stdout)
                SmtResult.Model(model)
            }
            else -> SmtResult.Unknown("Unexpected Z3 output: ${result.stdout.take(200)}")
        }
    }

    fun evaluatePathCondition(
        precondition: String,
        postcondition: String,
        pathConstraints: List<String>
    ): SmtResult {
        val query = buildSmtQuery(precondition, postcondition, pathConstraints)
        return checkSat(query)
    }

    private fun buildSmtQuery(precondition: String, postcondition: String, pathConstraints: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("(set-logic QF_UFBV)")
        sb.appendLine("(assert $precondition)")
        for (constraint in pathConstraints) {
            sb.appendLine("(assert $constraint)")
        }
        sb.appendLine("(assert (not $postcondition))")
        sb.appendLine("(check-sat)")
        sb.appendLine("(get-model)")
        return sb.toString()
    }

    private fun extractModel(output: String): Map<String, String> {
        val model = mutableMapOf<String, String>()
        val pattern = java.util.regex.Pattern.compile("\\(define-fun\\s+(\\w+)\\s+\\(\\)\\s+\\w+\\s+(.+?)\\)")
        val matcher = pattern.matcher(output)
        while (matcher.find()) {
            val name = matcher.group(1)
            val value = matcher.group(2).trim()
            model[name] = value
        }
        return model
    }
}