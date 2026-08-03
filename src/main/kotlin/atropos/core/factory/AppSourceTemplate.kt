package atropos.core.factory

import java.util.Locale

/** Renders executable source and executable tests from the generic app spec. */
class AppSourceTemplate {
    fun mainSource(spec: AppProjectSpec, packageName: String): String =
        if (isCalculator(spec)) calculatorMain(packageName) else genericMain(spec, packageName)

    fun testSource(spec: AppProjectSpec, packageName: String): String =
        if (isCalculator(spec)) calculatorTest(packageName) else genericTest(packageName)

    private fun isCalculator(spec: AppProjectSpec): Boolean =
        spec.intent.name.lowercase(Locale.US).contains("calculator") ||
            spec.intent.features.any { it.lowercase(Locale.US) == "calculator" }

    private fun calculatorMain(packageName: String) = """
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

        fun runCalculator(args: List<String>): CliResult {
            if (args.isEmpty() || args.singleOrNull() == "--help") {
                return if (args.isEmpty()) CliResult(2, error = "usage: calculator <number operator number>")
                else CliResult(0, output = "usage: calculator <number operator number>")
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
            val result = runCalculator(args.toList())
            if (result.output.isNotEmpty()) println(result.output)
            if (result.error.isNotEmpty()) System.err.println(result.error)
            if (result.exitCode != 0) exitProcess(result.exitCode)
        }
    """.trimIndent() + "\n"

    private fun calculatorTest(packageName: String) = """
        package $packageName

        fun main() {
            check(evaluate("2 + 2") == 4.0)
            check(evaluate("8 / 2") == 4.0)
            check(runCalculator(listOf("2 + 2")).let { it.exitCode == 0 && it.output == "4.0" })
            check(runCalculator(listOf("2 +")).exitCode != 0)
            check(runCalculator(listOf("4 / 0")).exitCode != 0)
        }
    """.trimIndent() + "\n"

    private fun genericMain(spec: AppProjectSpec, packageName: String): String {
        val title = spec.intent.name.replaceFirstChar { it.titlecase(Locale.US) }
        return """
            package $packageName

            data class CliResult(val exitCode: Int, val output: String = "", val error: String = "")

            fun runApp(args: List<String>): CliResult = when {
                args.singleOrNull() == "--help" -> CliResult(0, "usage: ${spec.intent.name} [input]")
                args.isEmpty() -> CliResult(2, error = "usage: ${spec.intent.name} [input]")
                else -> CliResult(0, output = "${title}: " + args.joinToString(" "))
            }

            fun main(args: Array<String>) {
                val result = runApp(args.toList())
                if (result.output.isNotEmpty()) println(result.output)
                if (result.error.isNotEmpty()) System.err.println(result.error)
            }
        """.trimIndent() + "\n"
    }

    private fun genericTest(packageName: String) = """
        package $packageName

        fun main() {
            check(runApp(listOf("--help")).exitCode == 0)
            check(runApp(emptyList()).exitCode != 0)
            check(runApp(listOf("input")).output.isNotBlank())
        }
    """.trimIndent() + "\n"
}
