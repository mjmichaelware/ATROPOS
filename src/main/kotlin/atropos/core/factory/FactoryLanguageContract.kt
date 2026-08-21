/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * The language-specific artifact contract shared by generation and completion.
 * A verifier must inspect the ecosystem the scaffold actually emitted; a
 * Kotlin-only path test is not a language policy.
 */
data class FactoryLanguageAssessment(
    val language: ProjectLanguage?,
    val sourceFiles: List<String>,
    val testFiles: List<String>,
    val sourceValid: Boolean,
    val testsValid: Boolean,
    val detail: String
) {
    val canComplete: Boolean get() = sourceValid && testsValid
}

object FactoryLanguageContract {
    fun contentValid(language: ProjectLanguage, source: String, tests: String): Boolean =
        requiredSourceMarkers(language).all(source::contains) &&
            requiredTestMarkers(language).all(tests::contains)

    fun assess(languageName: String?, files: Map<String, String>): FactoryLanguageAssessment {
        val language = languageName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { name -> runCatching { ProjectLanguage.valueOf(name.uppercase()) }.getOrNull() }
            ?: infer(files.keys)
        if (language == null) {
            return FactoryLanguageAssessment(null, emptyList(), emptyList(), false, false, "language is not declared")
        }
        val sourceFiles = files.keys.filter { isSource(language, it) }.sorted()
        val testFiles = files.keys.filter { isTest(language, it) }.sorted()
        val sourceText = sourceFiles.joinToString("\n") { files.getValue(it) }
        val testText = testFiles.joinToString("\n") { files.getValue(it) }
        val sourceValid = sourceFiles.isNotEmpty() && requiredSourceMarkers(language).all(sourceText::contains)
        val testsValid = testFiles.isNotEmpty() && requiredTestMarkers(language).all(testText::contains)
        val sourceLabel = if (sourceFiles.isEmpty()) "${language.displayName} production source is missing" else "${language.displayName} production source"
        val testLabel = if (testFiles.isEmpty()) "${language.displayName} test artifacts are missing" else "${language.displayName} test artifacts"
        return FactoryLanguageAssessment(
            language,
            sourceFiles,
            testFiles,
            sourceValid,
            testsValid,
            "$sourceLabel; $testLabel"
        )
    }

    private fun infer(paths: Collection<String>): ProjectLanguage? = when {
        paths.any { it.startsWith("src/main/kotlin/") && it.endsWith(".kt") } -> ProjectLanguage.KOTLIN
        paths.any { it.startsWith("src/main/java/") && it.endsWith(".java") } -> ProjectLanguage.JAVA
        paths.any { it.startsWith("tests/") && it.endsWith(".py") } -> ProjectLanguage.PYTHON
        paths.any { it.endsWith(".ts") } -> ProjectLanguage.TYPESCRIPT
        paths.any { it.endsWith(".go") } -> ProjectLanguage.GO
        paths.any { it.endsWith(".rs") } -> ProjectLanguage.RUST
        paths.any { it.endsWith(".rb") } -> ProjectLanguage.RUBY
        paths.any { it.endsWith(".cs") } -> ProjectLanguage.CSHARP
        paths.any { it.endsWith(".php") } -> ProjectLanguage.PHP
        paths.any { it.endsWith(".swift") } -> ProjectLanguage.SWIFT
        paths.any { it.endsWith(".cpp") || it.endsWith(".cc") } -> ProjectLanguage.CPP
        paths.any { it.endsWith(".ex") || it.endsWith(".exs") } -> ProjectLanguage.ELIXIR
        paths.any { it.endsWith(".scala") } -> ProjectLanguage.SCALA
        paths.any { it.endsWith(".clj") } -> ProjectLanguage.CLOJURE
        paths.any { it.endsWith(".hs") } -> ProjectLanguage.HASKELL
        paths.any { it.endsWith(".dart") } -> ProjectLanguage.DART
        paths.any { it.endsWith(".zig") } -> ProjectLanguage.ZIG
        paths.any { it.endsWith(".lua") } -> ProjectLanguage.LUA
        paths.any { it.endsWith(".r") } -> ProjectLanguage.R
        paths.any { it.endsWith(".jl") } -> ProjectLanguage.JULIA
        paths.any { it.endsWith(".fs") || it.endsWith(".fsx") } -> ProjectLanguage.FSHARP
        paths.any { it.endsWith(".erl") } -> ProjectLanguage.ERLANG
        paths.any { it.endsWith(".pl") } -> ProjectLanguage.PERL
        paths.any { it.endsWith(".sh") } -> ProjectLanguage.BASH
        paths.any { it.endsWith(".ps1") } -> ProjectLanguage.POWERSHELL
        paths.any { it.endsWith(".m") } -> ProjectLanguage.OBJECTIVE_C
        paths.any { it.endsWith(".f90") || it.endsWith(".f95") } -> ProjectLanguage.FORTRAN
        paths.any { it.endsWith(".groovy") } -> ProjectLanguage.GROOVY
        paths.any { it.endsWith(".nim") } -> ProjectLanguage.NIM
        paths.any { it.endsWith(".sol") } -> ProjectLanguage.SOLIDITY
        else -> null
    }

    private fun isSource(language: ProjectLanguage, path: String): Boolean = when (language) {
        ProjectLanguage.KOTLIN -> path.startsWith("src/main/") && path.endsWith(".kt")
        ProjectLanguage.JAVA -> path.startsWith("src/main/") && path.endsWith(".java")
        ProjectLanguage.PYTHON -> path.endsWith(".py") && !path.startsWith("tests/")
        ProjectLanguage.TYPESCRIPT -> path.endsWith(".ts") && !path.endsWith(".test.ts")
        ProjectLanguage.GO -> path.endsWith(".go") && !path.endsWith("_test.go")
        ProjectLanguage.RUST -> path.endsWith(".rs") && !path.startsWith("tests/")
        ProjectLanguage.RUBY -> path.endsWith(".rb") && !path.startsWith("spec/")
        ProjectLanguage.CSHARP -> path.endsWith(".cs") && !path.startsWith("tests/")
        ProjectLanguage.PHP -> path.endsWith(".php") && !path.startsWith("tests/")
        ProjectLanguage.SWIFT -> path.endsWith(".swift") && !path.startsWith("Tests/")
        ProjectLanguage.CPP, ProjectLanguage.C, ProjectLanguage.OBJECTIVE_C ->
            (path.endsWith(".c") || path.endsWith(".cpp") || path.endsWith(".m")) && !path.contains("test")
        ProjectLanguage.ELIXIR -> path.endsWith(".ex") && !path.startsWith("test/")
        ProjectLanguage.SCALA -> path.endsWith(".scala") && !path.contains("Test")
        ProjectLanguage.CLOJURE -> path.endsWith(".clj") && !path.startsWith("test/")
        ProjectLanguage.HASKELL -> path.endsWith(".hs") && !path.contains("Test")
        ProjectLanguage.DART -> path.endsWith(".dart") && !path.startsWith("test/")
        ProjectLanguage.ZIG -> path.endsWith(".zig") && !path.contains("test")
        ProjectLanguage.LUA -> path.endsWith(".lua") && !path.startsWith("spec/")
        ProjectLanguage.R -> path.endsWith(".r") && !path.startsWith("tests/")
        ProjectLanguage.JULIA -> path.endsWith(".jl") && !path.startsWith("test/")
        ProjectLanguage.FSHARP -> (path.endsWith(".fs") || path.endsWith(".fsx")) && !path.contains("Test")
        ProjectLanguage.ERLANG -> path.endsWith(".erl") && !path.contains("_test")
        ProjectLanguage.PERL -> path.endsWith(".pl") && !path.startsWith("t/")
        ProjectLanguage.BASH -> path.endsWith(".sh") && !path.startsWith("tests/")
        ProjectLanguage.POWERSHELL -> path.endsWith(".ps1") && !path.contains("Test")
        ProjectLanguage.FORTRAN -> path.endsWith(".f90") || path.endsWith(".f95")
        ProjectLanguage.GROOVY -> path.endsWith(".groovy") && !path.contains("Test")
        ProjectLanguage.NIM -> path.endsWith(".nim") && !path.contains("test")
        ProjectLanguage.SOLIDITY -> path.endsWith(".sol") && !path.startsWith("test/")
    }

    private fun isTest(language: ProjectLanguage, path: String): Boolean = when (language) {
        ProjectLanguage.KOTLIN -> path.startsWith("src/test/") && path.endsWith(".kt")
        ProjectLanguage.JAVA -> path.startsWith("src/test/") && path.endsWith(".java")
        ProjectLanguage.PYTHON -> path.startsWith("tests/") && path.endsWith(".py")
        ProjectLanguage.TYPESCRIPT -> path.endsWith(".test.ts")
        ProjectLanguage.GO -> path.endsWith("_test.go")
        ProjectLanguage.RUST -> path.startsWith("tests/") || path.endsWith("_test.rs")
        ProjectLanguage.RUBY -> path.startsWith("spec/")
        ProjectLanguage.CSHARP, ProjectLanguage.PHP -> path.startsWith("tests/")
        ProjectLanguage.SWIFT -> path.startsWith("Tests/")
        ProjectLanguage.CPP, ProjectLanguage.C, ProjectLanguage.OBJECTIVE_C -> path.contains("test")
        ProjectLanguage.ELIXIR, ProjectLanguage.CLOJURE, ProjectLanguage.DART, ProjectLanguage.JULIA -> path.startsWith("test/")
        ProjectLanguage.SCALA, ProjectLanguage.HASKELL, ProjectLanguage.FSHARP, ProjectLanguage.GROOVY -> path.contains("Test")
        ProjectLanguage.ZIG, ProjectLanguage.NIM -> path.contains("test")
        ProjectLanguage.LUA -> path.startsWith("spec/")
        ProjectLanguage.R -> path.startsWith("tests/")
        ProjectLanguage.ERLANG -> path.contains("_test")
        ProjectLanguage.PERL -> path.startsWith("t/")
        ProjectLanguage.BASH -> path.startsWith("tests/")
        ProjectLanguage.POWERSHELL -> path.contains("Test")
        ProjectLanguage.FORTRAN -> path.contains("test")
        ProjectLanguage.SOLIDITY -> path.startsWith("test/")
    }

    private fun requiredSourceMarkers(language: ProjectLanguage): List<String> = when (language) {
        ProjectLanguage.KOTLIN -> listOf("fun main(")
        ProjectLanguage.JAVA -> listOf("public static String describe(", "public static void main(")
        ProjectLanguage.PYTHON -> listOf("def describe(")
        ProjectLanguage.TYPESCRIPT -> listOf("export function describe(")
        ProjectLanguage.GO -> listOf("func Describe(", "func main(")
        ProjectLanguage.RUST -> listOf("pub fn describe(")
        ProjectLanguage.RUBY -> listOf("def self.describe")
        ProjectLanguage.CSHARP -> listOf("public static string Describe(")
        ProjectLanguage.PHP -> listOf("function describe(")
        ProjectLanguage.SWIFT -> listOf("public func describe(")
        ProjectLanguage.CPP, ProjectLanguage.C, ProjectLanguage.OBJECTIVE_C -> listOf("describe(")
        ProjectLanguage.ELIXIR, ProjectLanguage.SCALA -> listOf("def describe")
        ProjectLanguage.CLOJURE -> listOf("defn describe")
        ProjectLanguage.HASKELL -> listOf("describe ::")
        ProjectLanguage.DART -> listOf("String describe(")
        ProjectLanguage.ZIG -> listOf("pub fn describe(")
        ProjectLanguage.LUA -> listOf("function M.describe(")
        ProjectLanguage.R -> listOf("describe <- function")
        ProjectLanguage.JULIA -> listOf("describe()")
        ProjectLanguage.FSHARP -> listOf("let describe")
        ProjectLanguage.ERLANG -> listOf("describe() ->")
        ProjectLanguage.PERL -> listOf("sub describe")
        ProjectLanguage.BASH -> listOf("describe()")
        ProjectLanguage.POWERSHELL -> listOf("function Get-Description")
        ProjectLanguage.FORTRAN -> listOf("function describe()")
        ProjectLanguage.GROOVY -> listOf("static String describe()")
        ProjectLanguage.NIM -> listOf("proc describe")
        ProjectLanguage.SOLIDITY -> listOf("DESCRIPTION", "contract Counter")
    }

    private fun requiredTestMarkers(language: ProjectLanguage): List<String> = when (language) {
        ProjectLanguage.KOTLIN -> listOf("fun main(", "check(")
        ProjectLanguage.JAVA -> listOf("Main.describe(", "AssertionError")
        ProjectLanguage.PYTHON -> listOf("def test_", "assert ")
        ProjectLanguage.TYPESCRIPT -> listOf("test(", "expect(")
        ProjectLanguage.GO -> listOf("func TestDescribe(", "testing")
        ProjectLanguage.RUST -> listOf("#[test]", "assert_")
        ProjectLanguage.RUBY -> listOf("RSpec.describe", "expect(")
        ProjectLanguage.CSHARP -> listOf("Program.Describe(", "Exception")
        ProjectLanguage.PHP -> listOf("describe()", "exit(1)")
        ProjectLanguage.SWIFT -> listOf("XCTAssertEqual(")
        ProjectLanguage.CPP, ProjectLanguage.C, ProjectLanguage.OBJECTIVE_C -> listOf("assert(")
        ProjectLanguage.ELIXIR -> listOf("assert ", ".describe()")
        ProjectLanguage.SCALA -> listOf("assert(", ".describe")
        ProjectLanguage.CLOJURE -> listOf("deftest", "clojure.test")
        ProjectLanguage.HASKELL -> listOf("describe ==", "fail")
        ProjectLanguage.DART -> listOf("test(", "expect(")
        ProjectLanguage.ZIG -> listOf("test ", "expectEqual")
        ProjectLanguage.LUA -> listOf("assert(")
        ProjectLanguage.R -> listOf("stopifnot(")
        ProjectLanguage.JULIA -> listOf("@test")
        ProjectLanguage.FSHARP -> listOf("describe ()", "failwith")
        ProjectLanguage.ERLANG -> listOf("describe_test", "assertEqual")
        ProjectLanguage.PERL -> listOf("Test::More", "::describe()")
        ProjectLanguage.BASH -> listOf("test ", "source ")
        ProjectLanguage.POWERSHELL -> listOf("Get-Description", "throw")
        ProjectLanguage.FORTRAN -> listOf("describe())", "error stop")
        ProjectLanguage.GROOVY -> listOf("App.describe()", "assert ")
        ProjectLanguage.NIM -> listOf("assert describe()")
        ProjectLanguage.SOLIDITY -> listOf("testDescription", "assertEq")
    }
}
