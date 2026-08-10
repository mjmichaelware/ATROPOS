package atropos.core.factory

/**
 * Rejects scaffold-shaped output before it enters the generated repository.
 * The guard checks the generic source contract and capability-specific behavior
 * markers; it does not execute or verify the generated program.
 */
class AppGeneratedBehaviorGuard {
    fun requireRealBehavior(spec: AppProjectSpec, files: Map<String, String>) {
        val mainSources = files.entries
            .filter { it.key.startsWith("src/main/") && it.key.endsWith(".kt") }
            .map { it.value }
        val testSources = files.entries
            .filter { it.key.startsWith("src/test/") && it.key.endsWith(".kt") }
            .map { it.value }
        require(mainSources.isNotEmpty()) { "generated application source is missing" }
        require(testSources.isNotEmpty()) { "generated application tests are missing" }
        val main = mainSources.joinToString("\n")
        val tests = testSources.joinToString("\n")

        require("fun main(" in main) { "generated application entrypoint is missing" }
        require("exitProcess(" in main) { "generated application has no nonzero exit path" }
        require(hasLineage(main) && hasLineage(tests)) {
            "generated source and tests must retain prompt lineage"
        }
        require("fun main(" in tests && "check(" in tests) {
            "generated application tests must contain executable assertions"
        }
        require(!isFeatureStringOnlyScaffold(main, tests)) {
            "generated application tests still contain scaffold-only assertions"
        }

        if (AppCapability.EXPRESSION in spec.intent.capabilities()) {
            require("fun evaluate(" in main) { "expression application evaluator is missing" }
            require(listOf("+", "-", "*", "/").all { "\"$it\"" in main }) {
                "expression application does not expose all required operators"
            }
            require("division by zero" in main) { "expression division-by-zero handling is missing" }
            require("4 / 0" in tests) { "expression error-path assertion is missing" }
        } else {
            require("fun runApp(" in main && "data class AppState" in main) {
                "generic application stateful behavior function is missing"
            }
            require("\"add\"" in main && "\"list\"" in main && "unknown command" in main) {
                "generic application command behavior is missing"
            }
            require("runApp(listOf(\"add\"" in tests && "runApp(listOf(\"list\"" in tests) {
                "generic application tests do not exercise real commands"
            }
            spec.intent.features
                .map { it.lowercase() }
                .filter { it !in setOf("add", "list", "feature", "help") }
                .distinct()
                .forEach { feature ->
                    require("\"$feature\" ->" in main) {
                        "generated application feature behavior is missing: $feature"
                    }
                    require("runApp(listOf(\"$feature\"" in tests) {
                        "generated application feature assertion is missing: $feature"
                    }
                }
        }
    }

    private fun hasLineage(source: String): Boolean =
        "// ATROPOS lineage:" in source &&
            "// prompt_sha256=" in source &&
            "// prompt_fingerprint=" in source &&
            "// prompt_spans=" in source

    private fun isFeatureStringOnlyScaffold(main: String, tests: String): Boolean =
        "isNotBlank()" in tests &&
            "fun runApp(" !in main &&
            "fun evaluate(" !in main
}
