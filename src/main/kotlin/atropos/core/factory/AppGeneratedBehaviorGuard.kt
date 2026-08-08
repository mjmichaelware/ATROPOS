package atropos.core.factory

/**
 * Rejects scaffold-shaped output before it enters the generated repository.
 * The guard checks the generic source contract and capability-specific behavior
 * markers; it does not execute or verify the generated program.
 */
class AppGeneratedBehaviorGuard {
    fun requireRealBehavior(spec: AppProjectSpec, files: Map<String, String>) {
        val main = files.entries
            .firstOrNull { it.key.startsWith("src/main/") && it.key.endsWith(".kt") }
            ?.value
            ?: error("generated application source is missing")
        val tests = files.entries
            .firstOrNull { it.key.startsWith("src/test/") && it.key.endsWith(".kt") }
            ?.value
            ?: error("generated application tests are missing")

        require("fun main(" in main) { "generated application entrypoint is missing" }
        require("exitProcess(" in main) { "generated application has no nonzero exit path" }
        require("fun main(" in tests && "check(" in tests) {
            "generated application tests must contain executable assertions"
        }
        require(!main.contains("println(\"Calculator:") && !tests.contains("isNotBlank()")) {
            "generated application still contains scaffold-only behavior"
        }

        if (AppCapability.ARITHMETIC in spec.intent.capabilities()) {
            require("fun evaluate(" in main) { "arithmetic application evaluator is missing" }
            require(listOf("+", "-", "*", "/").all { "\"$it\"" in main }) {
                "arithmetic application does not expose all required operators"
            }
            require("division by zero" in main) { "arithmetic division-by-zero handling is missing" }
            require("4 / 0" in tests) { "arithmetic error-path assertion is missing" }
        } else {
            require("fun runApp(" in main) { "generic application behavior function is missing" }
            require("--help" in main && "args.isEmpty()" in main) {
                "generic application usage and empty-input behavior are missing"
            }
        }
    }
}
