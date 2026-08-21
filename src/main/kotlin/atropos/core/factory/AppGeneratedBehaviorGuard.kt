package atropos.core.factory

/**
 * Rejects scaffold-shaped output before it enters the generated repository.
 * The guard checks the generic source contract and capability-specific behavior
 * markers; it does not execute or verify the generated program.
 */
class AppGeneratedBehaviorGuard {
    fun requireRealBehavior(spec: AppProjectSpec, files: Map<String, String>) {
        val packageName = AppProjectGenerator.safeName(spec.intent.name)
        val detection = ProjectLanguage.detect(spec.prompt)
        val language = ProjectLanguage.layoutFor(detection)

        // If the language is unsupported or generic, layout has no source/test path.
        // We skip behavior checks in this case as there is no code to guard.
        val layout = when (detection) {
            is ProjectLanguage.Detection.Unsupported -> LanguageScaffold.generic(detection.displayName)
            else -> LanguageScaffold.forLanguage(language, packageName, spec.intent.name)
        }
        if (layout.sourcePath.isBlank() || layout.testPath.isBlank()) {
            return
        }

        val main = files[layout.sourcePath] ?: throw IllegalArgumentException("generated application source is missing")
        val tests = files[layout.testPath] ?: throw IllegalArgumentException("generated application tests are missing")

        require(hasLineage(main, layout.commentPrefix) && hasLineage(tests, layout.commentPrefix)) {
            "generated source and tests must retain prompt lineage"
        }

        if (language == ProjectLanguage.KOTLIN) {
            require("fun main(" in main) { "generated application entrypoint is missing" }
            require("exitProcess(" in main) { "generated application has no nonzero exit path" }
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
        } else {
            requireLanguageBehavior(language, main, tests)
        }
    }

    private fun requireLanguageBehavior(
        language: ProjectLanguage,
        main: String,
        tests: String
    ) {
        // These predicates mirror LanguageScaffold's native function and test
        // forms. Checking only for the English word "describe" let an empty
        // or unrelated file pass for every non-Kotlin project.
        val sourceMarkers: List<String>
        val testMarkers: List<String>
        when (language) {
            ProjectLanguage.JAVA -> {
                sourceMarkers = listOf("public static String describe(", "public static void main(")
                testMarkers = listOf("Main.describe(", "AssertionError")
            }
            ProjectLanguage.PYTHON -> {
                sourceMarkers = listOf("def describe(")
                testMarkers = listOf("def test_", "assert ")
            }
            ProjectLanguage.TYPESCRIPT -> {
                sourceMarkers = listOf("export function describe(")
                testMarkers = listOf("test(", "expect(")
            }
            ProjectLanguage.GO -> {
                sourceMarkers = listOf("func Describe(", "func main(")
                testMarkers = listOf("func TestDescribe(", "testing")
            }
            ProjectLanguage.RUST -> {
                sourceMarkers = listOf("pub fn describe(")
                testMarkers = listOf("#[test]", "assert_")
            }
            ProjectLanguage.RUBY -> {
                sourceMarkers = listOf("def self.describe")
                testMarkers = listOf("RSpec.describe", "expect(")
            }
            ProjectLanguage.CSHARP -> {
                sourceMarkers = listOf("public static string Describe(")
                testMarkers = listOf("Program.Describe(", "Exception")
            }
            ProjectLanguage.PHP -> {
                sourceMarkers = listOf("function describe(")
                testMarkers = listOf("describe()", "exit(1)")
            }
            ProjectLanguage.SWIFT -> {
                sourceMarkers = listOf("public func describe(")
                testMarkers = listOf("XCTAssertEqual(")
            }
            ProjectLanguage.CPP, ProjectLanguage.C, ProjectLanguage.OBJECTIVE_C -> {
                sourceMarkers = listOf("describe(")
                testMarkers = listOf("assert(")
            }
            ProjectLanguage.ELIXIR -> {
                sourceMarkers = listOf("def describe")
                testMarkers = listOf("assert ", ".describe()")
            }
            ProjectLanguage.SCALA -> {
                sourceMarkers = listOf("def describe")
                testMarkers = listOf("assert(", ".describe")
            }
            ProjectLanguage.CLOJURE -> {
                sourceMarkers = listOf("defn describe")
                testMarkers = listOf("deftest", "clojure.test")
            }
            ProjectLanguage.HASKELL -> {
                sourceMarkers = listOf("describe ::")
                testMarkers = listOf("describe ==", "fail")
            }
            ProjectLanguage.DART -> {
                sourceMarkers = listOf("String describe(")
                testMarkers = listOf("test(", "expect(")
            }
            ProjectLanguage.ZIG -> {
                sourceMarkers = listOf("pub fn describe(")
                testMarkers = listOf("test ", "expectEqual")
            }
            ProjectLanguage.LUA -> {
                sourceMarkers = listOf("function M.describe(")
                testMarkers = listOf("assert(")
            }
            ProjectLanguage.R -> {
                sourceMarkers = listOf("describe <- function")
                testMarkers = listOf("stopifnot(")
            }
            ProjectLanguage.JULIA -> {
                sourceMarkers = listOf("describe()")
                testMarkers = listOf("@test")
            }
            ProjectLanguage.FSHARP -> {
                sourceMarkers = listOf("let describe")
                testMarkers = listOf("describe ()", "failwith")
            }
            ProjectLanguage.ERLANG -> {
                sourceMarkers = listOf("describe() ->")
                testMarkers = listOf("describe_test", "assertEqual")
            }
            ProjectLanguage.PERL -> {
                sourceMarkers = listOf("sub describe")
                testMarkers = listOf("Test::More", "::describe()")
            }
            ProjectLanguage.BASH -> {
                sourceMarkers = listOf("describe()")
                testMarkers = listOf("test ", "source ")
            }
            ProjectLanguage.POWERSHELL -> {
                sourceMarkers = listOf("function Get-Description")
                testMarkers = listOf("Get-Description", "throw")
            }
            ProjectLanguage.FORTRAN -> {
                sourceMarkers = listOf("function describe()")
                testMarkers = listOf("describe())", "error stop")
            }
            ProjectLanguage.GROOVY -> {
                sourceMarkers = listOf("static String describe()")
                testMarkers = listOf("App.describe()", "assert ")
            }
            ProjectLanguage.NIM -> {
                sourceMarkers = listOf("proc describe")
                testMarkers = listOf("assert describe()")
            }
            ProjectLanguage.SOLIDITY -> {
                sourceMarkers = listOf("DESCRIPTION", "contract Counter")
                testMarkers = listOf("testDescription", "assertEq")
            }
            ProjectLanguage.KOTLIN -> error("Kotlin is guarded by the Kotlin-specific contract")
        }
        require(FactoryLanguageContract.contentValid(language, main, tests)) {
            require(sourceMarkers.all { main.contains(it) }) {
                "generated ${language.displayName} application behavior is missing"
            }
            "generated ${language.displayName} application tests are missing expected assertions"
        }
    }

    private fun hasLineage(source: String, commentPrefix: String): Boolean =
        "$commentPrefix ATROPOS lineage:" in source &&
            "$commentPrefix prompt_sha256=" in source &&
            "$commentPrefix prompt_fingerprint=" in source &&
            "$commentPrefix prompt_spans=" in source

    private fun isFeatureStringOnlyScaffold(main: String, tests: String): Boolean =
        "isNotBlank()" in tests &&
            "fun runApp(" !in main &&
            "fun evaluate(" !in main
}
