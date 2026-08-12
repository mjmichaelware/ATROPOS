/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.data.lakehouse

/**
 * The keywords an atom's own language targets in the lakehouse.
 *
 * Deterministic, no model call — the same discipline as
 * [atropos.core.nl.NlCanonicalizer], and for the same reason: a probabilistic
 * keyword set would make the context attached to an atom differ between two
 * runs of the identical plan, and every artifact downstream carries a hash that
 * would then be meaningless.
 *
 * Lakehouse paths are human taxonomy tags — `E/networking/http`,
 * `I/uiux/a11y_design`, `C/languages/kotlin/syntax` — so the useful signal is
 * plain token overlap against path *segments*. That is why this produces words
 * rather than ontological codes: the registry is readable, and matching it with
 * numeric addresses would throw away the one property that makes it matchable.
 *
 * The alias table exists because an atom says "REST endpoint" where the path
 * says `http`, and "WCAG" where the path says `a11y_design`. Without it the
 * overlap is real but sparse, and an atom that plainly concerns HTTP would
 * retrieve nothing.
 */
object AtomKeywordExtractor {

    fun keywords(statement: String): List<String> {
        val tokens = statement.lowercase()
            .split(NON_WORD)
            .map { it.trim() }
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOP_WORDS }

        val expanded = tokens.flatMap { token -> listOf(token) + ALIASES.getOrDefault(token, emptyList()) }

        // Ordered by first appearance rather than frequency. A statement's
        // opening clause names its subject; ranking by count would let a word
        // repeated in a subordinate clause outrank it.
        return expanded.distinct().take(MAX_KEYWORDS)
    }

    private val NON_WORD = Regex("[^a-z0-9_]+")

    private const val MIN_TOKEN_LENGTH = 3
    private const val MAX_KEYWORDS = 24

    /**
     * What an atom is likely to say, mapped to what a path segment calls it.
     *
     * Deliberately small and hand-written. A generated or learned table would
     * drift from the registry it exists to match, and the registry is the
     * thing that is actually authoritative about its own vocabulary.
     */
    val ALIASES: Map<String, List<String>> = mapOf(
        "rest" to listOf("http"),
        "endpoint" to listOf("http"),
        "request" to listOf("http"),
        "response" to listOf("http"),
        "status" to listOf("http"),
        "header" to listOf("http"),
        "ssl" to listOf("tls"),
        "certificate" to listOf("tls"),
        "https" to listOf("tls", "http"),
        "oauth" to listOf("auth_protocols"),
        "jwt" to listOf("auth_protocols"),
        "token" to listOf("auth_protocols"),
        "credential" to listOf("auth_protocols"),
        "coroutine" to listOf("kotlin"),
        "suspend" to listOf("kotlin"),
        "jvm" to listOf("kotlin"),
        "gradle" to listOf("kotlin"),
        "aria" to listOf("a11y_design", "uiux"),
        "wcag" to listOf("a11y_design", "uiux"),
        "accessibility" to listOf("a11y_design", "uiux"),
        "contrast" to listOf("a11y_design", "uiux"),
        "screenreader" to listOf("a11y_design"),
        "rebase" to listOf("git"),
        "branch" to listOf("git"),
        "commit" to listOf("git"),
        "merge" to listOf("git"),
        "worktree" to listOf("git"),
        "sqlite" to listOf("database"),
        "query" to listOf("database"),
        "schema" to listOf("database"),
        "compose" to listOf("android"),
        "activity" to listOf("android"),
        "apk" to listOf("android")
    )

    /**
     * Words that appear in every requirement and therefore distinguish none.
     *
     * Requirement prose is dense with "must", "shall" and "system"; leaving
     * them in makes every atom match every path that happens to contain them.
     */
    val STOP_WORDS: Set<String> = setOf(
        "the", "and", "for", "that", "this", "with", "must", "shall", "not",
        "are", "was", "were", "has", "have", "had", "its", "it", "from",
        "any", "all", "each", "every", "which", "when", "then", "than",
        "into", "onto", "over", "under", "such", "same", "other", "more",
        "system", "systems", "file", "files", "code", "value", "values",
        "used", "using", "use", "one", "two", "can", "cannot", "may",
        "should", "would", "could", "will", "does", "done", "make", "made",
        "also", "only", "but", "because", "while", "where", "what", "how"
    )
}
