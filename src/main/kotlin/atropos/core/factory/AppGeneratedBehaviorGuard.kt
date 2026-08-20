/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * Rejects scaffold-shaped output before it enters the generated repository.
 * The guard checks the source contract and capability-specific behavior
 * markers; it does not execute or verify the generated program.
 *
 * Every check used to be spelled in Kotlin -- a `.kt` file under `src/main`, `fun main(`,
 * `exitProcess(`, `// ATROPOS lineage:`. That was invisible while every project
 * was Kotlin. The moment the factory laid a Python repository out correctly,
 * the guard found no `.kt` files and aborted the whole run with "generated
 * application source is missing", which is a true sentence about the wrong
 * language. So the contract is read from the project's own scaffold now: the
 * behavior required is the same everywhere, the words for it are not.
 */
class AppGeneratedBehaviorGuard {

    fun requireRealBehavior(spec: AppProjectSpec, files: Map<String, String>) {
        val resolved = ProjectLayout.resolve(spec)
        val detection = resolved.detection
        // Refusing here, rather than writing a repository whose verify.sh
        // cannot pass, means the operator hears it at the moment they ask.
        if (detection is ProjectLanguage.Detection.Unsupported) {
            throw IllegalArgumentException(
                "ATROPOS has no scaffold for ${detection.displayName}; " +
                    "name a language it can lay out, or scaffold the project yourself"
            )
        }
        if (resolved.language == ProjectLanguage.KOTLIN) {
            requireKotlinBehavior(spec, files)
        } else {
            requireLanguageBehavior(spec, files, resolved)
        }
    }

    /**
     * The contract every generated program meets, whatever it is written in.
     *
     * Kotlin keeps its own checks below because [AppSourceTemplate] renders web,
     * service, desktop and expression variants that [LanguageBehaviorTemplate]
     * does not translate. Everything else gets the one CLI contract.
     */
    private fun requireLanguageBehavior(
        spec: AppProjectSpec,
        files: Map<String, String>,
        layout: ProjectLayout
    ) {
        val language = layout.language
        val name = language.displayName

        val main = files[layout.sourcePath]
        val tests = files[layout.testPath]
        require(!main.isNullOrBlank()) { "generated $name source is missing at ${layout.sourcePath}" }
        require(!tests.isNullOrBlank()) { "generated $name tests are missing at ${layout.testPath}" }

        val markers = MARKERS.getValue(language)
        require(markers.entrypoint in main) { "generated $name entrypoint is missing" }
        require(markers.nonZeroExit in main) { "generated $name has no nonzero exit path" }
        require(markers.runnerDefinition in main) { "generated $name command runner is missing" }
        require(hasLineage(main, layout.scaffold.commentPrefix) && hasLineage(tests, layout.scaffold.commentPrefix)) {
            "generated $name source and tests must retain prompt lineage"
        }

        require(listOf("\"add\"", "'add'").any { it in main }) { "generated $name add command is missing" }
        require(listOf("\"list\"", "'list'").any { it in main }) { "generated $name list command is missing" }
        require("unknown command" in main) { "generated $name unknown-command path is missing" }

        require(markers.runnerCall in tests) { "generated $name tests do not call the command runner" }
        require("add" in tests && "list" in tests && "unknown" in tests) {
            "generated $name tests do not exercise real commands"
        }

        // A feature the operator named that the program cannot run is a
        // scaffold pretending to be an application.
        spec.intent.features
            .map { it.lowercase() }
            .filter { it !in RESERVED_COMMANDS }
            .filter { SAFE_TOKEN.matches(it) }
            .distinct()
            .forEach { feature ->
                require(feature in main) { "generated $name feature behavior is missing: $feature" }
                require(feature in tests) { "generated $name feature assertion is missing: $feature" }
            }
    }

    private fun requireKotlinBehavior(spec: AppProjectSpec, files: Map<String, String>) {
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
        require(hasLineage(main, "//") && hasLineage(tests, "//")) {
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
        } else if (spec.intent.kind.lowercase() in setOf("web", "service", "desktop")) {
            val mode = when (spec.intent.kind.lowercase()) {
                "web" -> "--serve"
                "service" -> "--start"
                else -> "--gui"
            }
            require("fun runApp(" in main) { "${spec.intent.kind} application runner is missing" }
            require(mode in main) { "${spec.intent.kind} application mode is missing" }
            require("runApp(listOf(\"$mode\")" in tests) {
                "${spec.intent.kind} application tests do not exercise its launch mode"
            }
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
                .filter { it !in RESERVED_COMMANDS }
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

    private fun hasLineage(source: String, mark: String): Boolean =
        "$mark ATROPOS lineage:" in source &&
            "$mark prompt_sha256=" in source &&
            "$mark prompt_fingerprint=" in source &&
            "$mark prompt_spans=" in source

    private fun isFeatureStringOnlyScaffold(main: String, tests: String): Boolean =
        "isNotBlank()" in tests &&
            "fun runApp(" !in main &&
            "fun evaluate(" !in main

    /**
     * What an entrypoint, a non-zero exit and a command runner look like, per
     * language. The runner is two markers, not one: the source has to declare
     * it and the tests have to call it, and those are different spellings in
     * every one of these languages.
     */
    private data class Markers(
        val entrypoint: String,
        val nonZeroExit: String,
        val runnerDefinition: String,
        val runnerCall: String
    )

    private companion object {
        val RESERVED_COMMANDS = setOf("add", "list", "feature", "help")
        val SAFE_TOKEN = Regex("[a-z][a-z0-9_-]{0,31}")

        val MARKERS = mapOf(
            ProjectLanguage.PYTHON to Markers("def main(", "sys.exit(", "def run_app(", "run_app("),
            ProjectLanguage.TYPESCRIPT to
                Markers("export function main(", "process.exit(", "export function runApp(", "runApp("),
            ProjectLanguage.GO to Markers("func main(", "os.Exit(", "func RunApp(", "RunApp("),
            ProjectLanguage.RUST to Markers("fn main(", "process::exit(", "pub fn run_app(", "run_app("),
            ProjectLanguage.JAVA to
                Markers("public static void main(", "System.exit(", "CliResult runApp(", "runApp("),
            ProjectLanguage.RUBY to Markers("def self.main(", "exit(", "def self.run_app(", ".run_app("),
            ProjectLanguage.CSHARP to
                Markers("public static int Main(", "return result.ExitCode", "CliResult RunApp(", "RunApp("),
            ProjectLanguage.PHP to Markers("function main(", "exit(", "function run_app(", "run_app("),
            ProjectLanguage.SWIFT to Markers("public func main(", "exit(", "public func runApp(", "runApp("),
            ProjectLanguage.CPP to Markers("int main(", "return result.exit_code", "CliResult run_app(", "run_app(")
        )
    }
}
