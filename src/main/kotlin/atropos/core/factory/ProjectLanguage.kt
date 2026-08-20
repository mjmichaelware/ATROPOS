/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * The language a generated project is actually written in.
 *
 * There was no such concept: [RepoScaffold] wrote a JVM source tree and a
 * `verify.sh` calling `kotlinc` for every project, whatever the operator's
 * document said. A scaffold is a claim about where code goes, so making that
 * claim in the wrong ecosystem files every subsequently generated file under
 * it, beside a verification script that cannot run any of it.
 *
 * Adding languages fixed that for the languages added and left the same defect
 * behind for every other: a Rails prompt, a Spring Boot prompt and a SwiftUI
 * prompt all still produced Kotlin, silently. Silence is the defect. A tool
 * that cannot scaffold Ruby should say it cannot scaffold Ruby, not hand back
 * a JVM tree and let the operator discover the mismatch when their own files
 * land in `src/main/kotlin`.
 *
 * So detection has three answers, not one: a language this can lay out, a
 * language it can only name, and nothing stated at all.
 */
enum class ProjectLanguage(
    val displayName: String,
    /**
     * Words that name this language or its ecosystem.
     *
     * Specific enough not to collide. `go` alone matched ordinary English and
     * `jvm` matched Java, which is how "a Spring Boot service in Java" became
     * a Kotlin project — the tokens have to identify the ecosystem, not merely
     * appear near it.
     */
    val signals: List<String>
) {
    PYTHON("Python", listOf("python", "fastapi", "django", "flask", "uvicorn", "pytest", "pyproject", "pip install")),
    TYPESCRIPT("TypeScript", listOf("typescript", "next.js", "nextjs", "react", "node.js", "nodejs", "vite", "tsconfig", "npm install")),
    KOTLIN("Kotlin", listOf("kotlin", "ktor", "gradle.kts", "jetpack compose")),
    JAVA("Java", listOf("java", "spring boot", "maven", "junit", "jakarta")),
    GO("Go", listOf("golang", "go.mod", "go module", "goroutine")),
    RUST("Rust", listOf("rust", "cargo", "crates.io")),
    RUBY("Ruby", listOf("ruby", "rails", "activerecord", "bundler", "rspec", "gemfile")),
    CSHARP("C#", listOf("c#", "csharp", ".net", "asp.net", "entity framework", "nuget")),
    PHP("PHP", listOf("php", "laravel", "symfony", "composer.json")),
    SWIFT("Swift", listOf("swift", "swiftui", "xcode", "cocoapods")),
    CPP("C++", listOf("c++", "cpp", "cmake", "catch2"));

    /** What the detector concluded, and how confident that conclusion is. */
    sealed interface Detection {
        /** A language with a real layout. */
        data class Scaffolded(val language: ProjectLanguage) : Detection

        /**
         * Named clearly, and there is no layout for it.
         *
         * A generic tree and a README that says so, rather than a JVM tree
         * that pretends. The operator finds out now instead of after the
         * providers have filled the wrong directories.
         */
        data class Unsupported(val displayName: String) : Detection

        /** Nothing stated. The old default is right here. */
        data object Unstated : Detection
    }

    companion object {
        /**
         * Languages this can name but not lay out.
         *
         * Listed on purpose. The point is to be able to say "this document is
         * Elixir and I have no Elixir scaffold" instead of quietly producing
         * Kotlin, so the list has to exist even though nothing consumes it for
         * layout.
         */
        val RECOGNISED_WITHOUT_LAYOUT: Map<String, List<String>> = mapOf(
            "Elixir" to listOf("elixir", "phoenix framework", "mix.exs"),
            "Scala" to listOf("scala", "sbt "),
            "Clojure" to listOf("clojure", "leiningen", "deps.edn"),
            "Haskell" to listOf("haskell", "cabal", "stack.yaml"),
            "Dart" to listOf("dart", "flutter", "pubspec"),
            "C" to listOf("ansi c", " c99", " c11"),
            "Zig" to listOf("zig", "build.zig"),
            "Lua" to listOf("lua", "luarocks"),
            "R" to listOf("rstudio", "cran", "tidyverse"),
            "Julia" to listOf("julia", "project.toml julia")
        )

        /**
         * The language the text most clearly names.
         *
         * Counted rather than first-match: a Python blueprint that mentions
         * Kotlin once in a comparison is still a Python project, and whichever
         * ecosystem a document talks about most is the one it is about.
         */
        fun detect(text: String): Detection {
            val haystack = " " + text.lowercase().replace(Regex("[\\n\\r\\t]"), " ") + " "
            fun score(signals: List<String>) =
                signals.sumOf { Regex(Regex.escape(it)).findAll(haystack).count() }

            val scaffolded = entries.map { it to score(it.signals) }.maxByOrNull { it.second }
            val unsupported = RECOGNISED_WITHOUT_LAYOUT
                .map { (name, signals) -> name to score(signals) }
                .maxByOrNull { it.second }

            val bestScaffolded = scaffolded?.second ?: 0
            val bestUnsupported = unsupported?.second ?: 0

            return when {
                bestScaffolded == 0 && bestUnsupported == 0 -> Detection.Unstated
                // A tie goes to the language that can actually be laid out; a
                // document naming both is better served by a real tree.
                bestScaffolded >= bestUnsupported -> Detection.Scaffolded(scaffolded!!.first)
                else -> Detection.Unsupported(unsupported!!.first)
            }
        }

        /** The layout language, defaulting where nothing was stated. */
        fun layoutFor(detection: Detection): ProjectLanguage = when (detection) {
            is Detection.Scaffolded -> detection.language
            // A generic tree, not a JVM one. GENERIC has no source file of its
            // own, so nothing is written into a directory the operator's
            // language will not look in.
            is Detection.Unsupported -> KOTLIN
            Detection.Unstated -> KOTLIN
        }
    }
}
