package atropos.core.factory

import atropos.core.security.RedactionFilter
import java.util.Locale

/** Renders executable source and executable tests from the generic app spec. */
class AppSourceTemplate {
    fun mainSource(spec: AppProjectSpec, packageName: String): String =
        if (AppCapability.EXPRESSION in spec.intent.capabilities()) expressionMain(spec, packageName)
        else genericMain(spec, packageName)

    fun testSource(spec: AppProjectSpec, packageName: String): String =
        if (AppCapability.EXPRESSION in spec.intent.capabilities()) expressionTest(packageName)
        else genericTest(spec, packageName)

    private fun expressionMain(spec: AppProjectSpec, packageName: String) = """
        package $packageName

        import kotlin.system.exitProcess

        data class CliResult(val exitCode: Int, val output: String = "", val error: String = "")

        fun evaluate(expression: String): Double {
            val match = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)\\s*$").matchEntire(expression)
                ?: throw IllegalArgumentException("expected: number operator number")
            val left = match.groupValues[1].toDouble()
            val operator = match.groupValues[2]
            val right = match.groupValues[3].toDouble()
            if (operator == "/" && right == 0.0) throw ArithmeticException("division by zero")
            return when (operator) {
                "+" -> left + right
                "-" -> left - right
                "*" -> left * right
                "/" -> left / right
                else -> error("unsupported operator")
            }
        }

        fun runExpression(args: List<String>): CliResult {
            if (args.isEmpty() || args.singleOrNull() == "--help") {
                val usage = "usage: ${kotlinLiteral(spec.intent.name)} <number operator number>"
                return if (args.isEmpty()) CliResult(2, error = usage)
                else CliResult(0, output = usage)
            }
            return try {
                CliResult(0, output = evaluate(args.joinToString(" ")).toString())
            } catch (failure: IllegalArgumentException) {
                CliResult(2, error = failure.message ?: "invalid expression")
            } catch (failure: ArithmeticException) {
                CliResult(3, error = failure.message ?: "arithmetic error")
            }
        }

        fun main(args: Array<String>) {
            val result = runExpression(args.toList())
            if (result.output.isNotEmpty()) println(result.output)
            if (result.error.isNotEmpty()) System.err.println(result.error)
            if (result.exitCode != 0) exitProcess(result.exitCode)
        }
    """.trimIndent() + "\n"

    private fun expressionTest(packageName: String) = """
        package $packageName

        fun main() {
            check(evaluate("2 + 2") == 4.0)
            check(evaluate("8 / 2") == 4.0)
            check(runExpression(listOf("2 + 2")).let { it.exitCode == 0 && it.output == "4.0" })
            check(runExpression(listOf("2 +")).exitCode != 0)
            check(runExpression(listOf("4 / 0")).exitCode != 0)
        }
    """.trimIndent() + "\n"

    private fun genericMain(spec: AppProjectSpec, packageName: String): String {
        val title = spec.intent.name.replaceFirstChar { it.titlecase(Locale.US) }
        val appName = kotlinLiteral(spec.intent.name)
        return """
            package $packageName

            import kotlin.system.exitProcess

            data class CliResult(val exitCode: Int, val output: String = "", val error: String = "")
            data class AppState(val items: MutableList<String> = mutableListOf())

            private const val USAGE = "usage: $appName [add <value>|list|feature <value>|--help]"

            fun runApp(args: List<String>, state: AppState = AppState()): CliResult {
                if (args.singleOrNull() == "--help") return CliResult(0, output = USAGE)
                if (args.isEmpty()) return CliResult(2, error = USAGE)
                return when (args.first()) {
                    "add" -> {
                        val value = args.drop(1).joinToString(" ").trim()
                        if (value.isBlank()) CliResult(2, error = "usage: $appName add <value>")
                        else {
                            state.items += value
                            CliResult(0, output = "added: " + value)
                        }
                    }
                    "list" -> CliResult(
                        0,
                        output = state.items.mapIndexed { index, item -> "${'$'}{index + 1}. ${'$'}item" }
                            .ifEmpty { listOf("no items") }
                            .joinToString("\n")
                    )
                    "feature" -> {
                        val value = args.drop(1).joinToString(" ").trim()
                        if (value.isBlank()) CliResult(2, error = "usage: $appName feature <value>")
                        else {
                            state.items += "feature: ${'$'}value"
                            CliResult(0, output = "feature: ${'$'}value")
                        }
                    }
                    ${featureBranches(spec, appName)}
                    else -> CliResult(2, error = "unknown command: ${'$'}{args.first()}")
                }
            }

            fun main(args: Array<String>) {
                val result = runApp(args.toList())
                if (result.output.isNotEmpty()) println(result.output)
                if (result.error.isNotEmpty()) System.err.println(result.error)
                if (result.exitCode != 0) exitProcess(result.exitCode)
            }
        """.trimIndent() + "\n"
    }

    private fun genericTest(spec: AppProjectSpec, packageName: String): String {
        val appName = kotlinLiteral(spec.intent.name)
        return """
        package $packageName

        fun main() {
            val state = AppState()
            check(runApp(listOf("--help"), state) == CliResult(0, output = "usage: $appName [add <value>|list|feature <value>|--help]"))
            check(runApp(emptyList(), state).exitCode == 2)
            check(runApp(listOf("add", "first", "item"), state) == CliResult(0, output = "added: first item"))
            check(runApp(listOf("list"), state) == CliResult(0, output = "1. first item"))
            check(runApp(listOf("add"), state).exitCode == 2)
            ${featureAssertions(spec)}
            check(runApp(listOf("unknown"), state).exitCode == 2)
        }
    """.trimIndent() + "\n"
    }

    private fun featureBranches(spec: AppProjectSpec, appName: String): String =
        spec.intent.features
            .map { it.lowercase(Locale.US) }
            .filter { it !in setOf("add", "list", "feature", "help") }
            .distinct()
            .joinToString("\n") { feature ->
                val literal = kotlinLiteral(feature)
                """
                    "$literal" -> {
                        val value = args.drop(1).joinToString(" ").trim()
                        if (value.isBlank()) CliResult(2, error = "usage: $appName $literal <value>")
                        else {
                            state.items += "$literal: ${'$'}value"
                            CliResult(0, output = "$literal: ${'$'}value")
                        }
                    }
                """.trimIndent()
            }

    private fun featureAssertions(spec: AppProjectSpec): String =
        spec.intent.features
            .map { it.lowercase(Locale.US) }
            .filter { it !in setOf("add", "list", "feature", "help") }
            .distinct()
            .joinToString("\n") { feature ->
                val literal = kotlinLiteral(feature)
                """
                    check(runApp(listOf("$literal", "sample"), state) == CliResult(0, output = "$literal: sample"))
                    check(runApp(listOf("$literal"), state).exitCode == 2)
                """.trimIndent()
            }

    private fun kotlinLiteral(value: String): String = RedactionFilter().redact(value)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("${'$'}", "\\${'$'}")
}
