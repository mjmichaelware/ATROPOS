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
            "A game engine in C++ with CMake" to ProjectLanguage.CPP
        ).forEach { (prompt, expected) ->
            assertEquals(
                ProjectLanguage.Detection.Scaffolded(expected),
                ProjectLanguage.detect(prompt),
                "'$prompt' still resolves to the wrong language"
            )
        }
    }

    @Test
    fun a_language_it_cannot_lay_out_is_named_rather_than_faked() {
        // Silence was the defect. A tool that cannot scaffold Elixir should
        // say so, not hand back a JVM tree and let the operator discover the
        // mismatch when their files land in src/main/kotlin.
        val detection = ProjectLanguage.detect("An Elixir Phoenix framework app built with mix.exs")

        assertTrue(detection is ProjectLanguage.Detection.Unsupported, detection.toString())
        assertEquals("Elixir", (detection as ProjectLanguage.Detection.Unsupported).displayName)
    }

    @Test
    fun an_unsupported_language_gets_no_source_tree_and_an_honest_verify() {
        val files = scaffold("An Elixir Phoenix framework app built with mix.exs")

        assertFalse(
            files.keys.any { it.contains("src/main/kotlin") },
            "an Elixir project was given a JVM source tree: ${files.keys}"
        )
        assertTrue(files.getValue("README.md").contains("no scaffold for Elixir"), files.getValue("README.md"))
        assertTrue(files.getValue("verify.sh").contains("no scaffold for Elixir"), files.getValue("verify.sh"))
        // It refuses rather than printing the success marker, so nothing
        // downstream can read it as a passing verification.
        assertFalse(files.getValue("verify.sh").contains("APP_FACTORY_VERIFY_OK"))
    }

    @Test
    fun nothing_stated_is_a_different_answer_from_a_language_named() {
        assertEquals(ProjectLanguage.Detection.Unstated, ProjectLanguage.detect("track my expenses"))
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
