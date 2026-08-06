package atropos.core.factory

import java.util.Locale

/** Renders executable source and executable tests from the generic app spec. */
class AppSourceTemplate {
    fun mainSource(spec: AppProjectSpec, packageName: String): String =
        if (AppCapability.ARITHMETIC in spec.intent.capabilities()) arithmeticMain(spec, packageName)
        else genericMain(spec, packageName)

    fun testSource(spec: AppProjectSpec, packageName: String): String =
        if (AppCapability.ARITHMETIC in spec.intent.capabilities()) arithmeticTest(packageName)
        else genericTest(spec, packageName)

    private fun arithmeticMain(spec: AppProjectSpec, packageName: String) = """
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

    private fun arithmeticTest(packageName: String) = """
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
        val appTitle = kotlinLiteral(title)
        return """
            package $packageName

            import kotlin.system.exitProcess

            data class CliResult(val exitCode: Int, val output: String = "", val error: String = "")

            private const val USAGE = "usage: $appName [input]"

            fun runApp(args: List<String>): CliResult = when {
                args.singleOrNull() == "--help" -> CliResult(0, output = USAGE)
                args.isEmpty() -> CliResult(2, error = USAGE)
                else -> CliResult(0, output = "$appTitle: " + args.joinToString(" "))
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
        val appTitle = kotlinLiteral(spec.intent.name.replaceFirstChar { it.titlecase(Locale.US) })
        return """
        package $packageName

        fun main() {
            check(runApp(listOf("--help")) == CliResult(0, output = "usage: $appName [input]"))
            check(runApp(emptyList()) == CliResult(2, error = "usage: $appName [input]"))
            check(runApp(listOf("input", "value")) == CliResult(0, output = "$appTitle: input value"))
        }
    """.trimIndent() + "\n"
    }

    private fun kotlinLiteral(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("${'$'}", "\\${'$'}")
}
