/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * The language a generated project is actually written in.
 *
 * There was no such concept. [RepoScaffold] wrote
 * `src/main/kotlin/<name>/Main.kt` and a `verify.sh` that shells out to
 * `kotlinc`, for every project, whatever the operator's document said. A
 * specification opening with "Engine language: Python 3.11+, Web framework:
 * FastAPI" produced a JVM source tree -- and then every file a provider wrote
 * into that repository landed in a directory layout for the wrong ecosystem,
 * next to a verification script that could not run any of it.
 *
 * A scaffold is a claim about where code goes. Making the claim in one
 * language regardless of the request is worse than making no claim at all,
 * because the operator's own files end up filed under it.
 *
 * Detected from what the document says rather than asked for separately: a
 * blueprint that names its stack in the first ten lines should not need the
 * operator to repeat it in a flag.
 */
enum class ProjectLanguage(
    val displayName: String,
    /** Words in a prompt that name this language or its ecosystem. */
    val signals: List<String>
) {
    PYTHON(
        "Python",
        listOf("python", "fastapi", "django", "flask", "uvicorn", "pytest", "pip", "pyproject", "conda")
    ),
    TYPESCRIPT(
        "TypeScript",
        listOf("typescript", "next.js", "nextjs", "react", "node.js", "nodejs", "npm", "vite", "deno", "tsx")
    ),
    KOTLIN(
        "Kotlin",
        listOf("kotlin", "gradle", "jvm", "ktor", "android", "compose")
    ),
    GO("Go", listOf("golang", " go ", "go module")),
    RUST("Rust", listOf("rust", "cargo", "crates.io"));

    companion object {
        /**
         * The language the text most clearly names, or [KOTLIN] when it names
         * none.
         *
         * Counted rather than first-match: a Python blueprint that mentions
         * Kotlin once in a comparison should not become a Kotlin project, and
         * whichever ecosystem the document talks about most is the one it is
         * about. Ties fall to the earlier declaration, which is where a
         * blueprint states its stack.
         *
         * The default stays Kotlin so no existing generated project changes
         * shape; a document that names nothing is exactly the case the old
         * behaviour was right for.
         */
        fun detect(text: String): ProjectLanguage {
            val haystack = " " + text.lowercase().replace(Regex("[\\n\\r\\t]"), " ") + " "
            val scored = entries.map { language ->
                language to language.signals.sumOf { signal ->
                    Regex(Regex.escape(signal)).findAll(haystack).count()
                }
            }
            val best = scored.maxByOrNull { it.second } ?: return KOTLIN
            return if (best.second == 0) KOTLIN else best.first
        }
    }
}
