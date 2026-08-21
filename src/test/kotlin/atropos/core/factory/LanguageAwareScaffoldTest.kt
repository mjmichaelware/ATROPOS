/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A generated repository shaped like the language it is written in.
 *
 * There was no language concept in the factory at all: every project got
 * `src/main/kotlin/<name>/Main.kt` and a `verify.sh` that shells out to
 * `kotlinc`. A specification opening with "Engine language: Python 3.11+,
 * Web framework: FastAPI" produced a JVM source tree — and then every file a
 * provider wrote into that repository landed in a layout for the wrong
 * ecosystem, beside a verification script that could not run any of it.
 */
class LanguageAwareScaffoldTest {

    private fun scaffold(prompt: String, kind: String = "cli"): Map<String, String> =
        RepoScaffold().files(
            AppProjectSpec(
                prompt = prompt,
                intent = AppIntent(name = "musicmakerlm", kind = kind, features = listOf("generate"))
            )
        )

    private val pythonBlueprint = """
        MUSIC MAKERLM — BUILD BLUEPRINT
        Engine language: Python 3.11+
        Web framework: FastAPI + Uvicorn (async, auto API docs)
        Database: SQLite (dev); SQLAlchemy keeps it Postgres-ready
        Dev: pytest, black, ruff, mypy
    """.trimIndent()

    @Test
    fun a_python_blueprint_produces_a_python_tree() {
        val files = scaffold(pythonBlueprint)

        assertTrue(files.keys.any { it.endsWith(".py") }, files.keys.toString())
        assertTrue("pyproject.toml" in files, files.keys.toString())
        assertFalse(
            files.keys.any { it.contains("src/main/kotlin") },
            "a Python project was given a JVM source tree: ${files.keys}"
        )
    }

    @Test
    fun the_verification_script_can_run_the_language_it_scaffolded() {
        // A repository that fails its own verification for a reason unrelated
        // to its code is worse than one with no verification at all.
        val verify = scaffold(pythonBlueprint).getValue("verify.sh")

        assertTrue(verify.contains("pytest"), verify)
        assertFalse(verify.contains("kotlinc"), verify)
    }

    @Test
    fun the_provenance_header_does_not_break_the_file_it_documents() {
        // `//` at the top of a Python file is a syntax error.
        val files = RepoScaffold().files(
            AppProjectSpec(
                prompt = pythonBlueprint,
                intent = AppIntent("musicmakerlm", "cli", listOf("generate"))
            ),
            FactoryLineage(
                promptFingerprint = "fp",
                promptSha256 = "a".repeat(64),
                researchSha256 = "b".repeat(64),
                researchDocument = "",
                promptDocument = pythonBlueprint,
                projectId = "p",
                confidence = FactoryConfidence(score = 90, breakdown = "", questions = emptyList()),
                promptSpans = "1"
            )
        )
        val source = files.entries.first { it.key.endsWith(".py") }.value

        assertTrue(source.startsWith("#"), source.take(80))
        assertFalse(source.lineSequence().first().startsWith("//"), source.take(80))
    }

    @Test
    fun a_typescript_prompt_produces_a_node_project() {
        val files = scaffold("Build a Next.js and TypeScript web app with npm and vite")

        assertTrue("package.json" in files, files.keys.toString())
        assertTrue("tsconfig.json" in files, files.keys.toString())
        assertTrue(files.keys.any { it.endsWith(".ts") }, files.keys.toString())
    }

    @Test
    fun a_document_that_names_no_language_still_gets_the_old_behaviour() {
        // The default has to stay Kotlin so no existing generated project
        // changes shape. A prompt that names nothing is exactly the case the
        // previous behaviour was right for.
        val files = scaffold("build me a tool that tracks expenses")

        assertTrue(files.keys.any { it.contains("src/main/kotlin") }, files.keys.toString())
    }

    @Test
    fun one_passing_mention_of_another_language_does_not_win() {
        // A Python blueprint that mentions Kotlin once in a comparison is
        // still a Python project.
        assertEquals(
            ProjectLanguage.Detection.Scaffolded(ProjectLanguage.PYTHON),
            ProjectLanguage.detect("$pythonBlueprint\n\nUnlike the Kotlin engine, this runs on Python.")
        )
    }

    @Test
    fun the_languages_that_used_to_become_kotlin_no_longer_do() {
        // Every one of these silently produced a JVM source tree. "A Spring
        // Boot service in Java" was the worst: `jvm` was a Kotlin signal, so
        // Java actively matched the wrong language.
        mapOf(
            "Build a Rails app with Ruby and ActiveRecord" to ProjectLanguage.RUBY,
            "A Spring Boot service in Java with Maven" to ProjectLanguage.JAVA,
            "An ASP.NET Core API in C# with Entity Framework" to ProjectLanguage.CSHARP,
            "A Laravel app in PHP with composer.json" to ProjectLanguage.PHP,
            "An iOS app in Swift with SwiftUI and Xcode" to ProjectLanguage.SWIFT,
            "A game engine in C++ with CMake" to ProjectLanguage.CPP,
            "An Elixir Phoenix service with mix.exs" to ProjectLanguage.ELIXIR,
            "A Scala service built with sbt" to ProjectLanguage.SCALA,
            "A Clojure service using deps.edn" to ProjectLanguage.CLOJURE,
            "A Haskell app built with cabal" to ProjectLanguage.HASKELL,
            "A Dart CLI with pubspec" to ProjectLanguage.DART,
            "An ANSI C11 program built with gcc" to ProjectLanguage.C,
            "A Zig tool using build.zig" to ProjectLanguage.ZIG,
            "A Lua script with luarocks" to ProjectLanguage.LUA,
            "An R package using testthat and Rscript" to ProjectLanguage.R,
            "A Julia package with Project.toml julia" to ProjectLanguage.JULIA,
            "An F# service using dotnet fsi" to ProjectLanguage.FSHARP,
            "An Erlang service with rebar3 and OTP" to ProjectLanguage.ERLANG,
            "A Perl module tested with prove" to ProjectLanguage.PERL,
            "A bash script with shellcheck" to ProjectLanguage.BASH,
            "A PowerShell script using pwsh" to ProjectLanguage.POWERSHELL,
            "An Objective-C app using objc and clang" to ProjectLanguage.OBJECTIVE_C,
            "A Fortran 90 program using gfortran" to ProjectLanguage.FORTRAN,
            "A Groovy app with Gradle Groovy" to ProjectLanguage.GROOVY,
            "A Nim app using nimble" to ProjectLanguage.NIM,
            "A Solidity contract using forge" to ProjectLanguage.SOLIDITY
        ).forEach { (prompt, expected) ->
            assertEquals(
                ProjectLanguage.Detection.Scaffolded(expected),
                ProjectLanguage.detect(prompt),
                "'$prompt' still resolves to the wrong language"
            )
        }
    }

    @Test
    fun every_catalogued_language_has_a_real_tree_manifest_and_verifier() {
        val prompts = listOf(
            "Elixir Phoenix with mix.exs", "Scala with sbt", "Clojure with deps.edn",
            "Haskell with cabal", "Dart with pubspec", "ANSI C11 with gcc",
            "Zig with build.zig", "Lua with luarocks", "R with Rscript", "Julia with Project.toml julia"
            , "F# with dotnet fsi", "Erlang with rebar3", "Perl with prove", "bash script with shellcheck",
            "PowerShell with pwsh", "Objective-C with clang", "Fortran with gfortran", "Groovy with Gradle Groovy",
            "Nim with nimble", "Solidity with forge"
        )
        prompts.forEach { prompt ->
            val files = scaffold(prompt)
            assertFalse(files.keys.any { it.contains("src/main/kotlin") }, files.keys.toString())
            assertTrue(files.keys.any { it.contains("test") || it.contains("tests") }, files.keys.toString())
            assertTrue(files.keys.any { it in setOf("mix.exs", "build.sbt", "deps.edn", "Project.toml", "DESCRIPTION", "pubspec.yaml", "Makefile", "build.zig", "rebar.config", "build.gradle", "foundry.toml", "README.ps1.md") || it.endsWith(".cabal") || it.endsWith(".rockspec") || it.endsWith(".fsproj") || it.endsWith(".nimble") }, files.keys.toString())
            assertTrue(files.getValue("verify.sh").contains("APP_FACTORY_VERIFY_OK"), files.getValue("verify.sh"))
        }
    }

    @Test
    fun nothing_stated_is_a_different_answer_from_a_language_named() {
        assertEquals(ProjectLanguage.Detection.Unstated, ProjectLanguage.detect("track my expenses"))
    }

    @Test
    fun named_language_without_adapter_is_refused_instead_of_becoming_kotlin() {
        assertEquals(
            ProjectLanguage.Detection.Unsupported("OCaml"),
            ProjectLanguage.detect("Build an OCaml service with dune")
        )
    }

    @Test
    fun web_assets_are_written_where_the_ecosystem_serves_them_from() {
        // JVM resource paths inside a Python project put the frontend
        // somewhere nothing will serve it from.
        val python = scaffold(pythonBlueprint, kind = "web")
        val kotlin = scaffold("a plain Kotlin gradle service", kind = "web")

        assertTrue(python.keys.any { it == "static/index.html" }, python.keys.toString())
        assertTrue(
            kotlin.keys.any { it == "src/main/resources/static/index.html" },
            kotlin.keys.toString()
        )
    }

    @Test
    fun every_language_ships_the_manifest_its_tooling_needs() {
        // Without one, a generated repository is a pile of source files that
        // no tool in its own language will install or run.
        mapOf(
            "python fastapi pytest" to "pyproject.toml",
            "typescript react npm" to "package.json",
            "golang go module" to "go.mod",
            "rust cargo crates.io" to "Cargo.toml"
        ).forEach { (prompt, manifest) ->
            assertTrue(manifest in scaffold(prompt), "$prompt did not produce $manifest")
        }
    }
}
