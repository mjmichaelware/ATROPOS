/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

/**
 * Words that carry no application meaning in a build request.
 *
 * Extracted from [IntentParser] because the list stopped being incidental. It
 * decides two separate things — what the generated application is *named*, and
 * what its declared *features* are — and both were wrong in the same way.
 *
 * The observed failure: "build me a todo list app that stores tasks in a file
 * and can mark them done" produced an application named **`me`**, because `me`
 * is the first token that is neither an action nor a stop word. The feature list
 * came out as `me, todo, list, that, stores, tasks, in, file, can, mark, them,
 * done` — six real features and six function words competing with them for the
 * twelve-item budget.
 *
 * The missing categories were pronouns and the small grammatical words that
 * connect a request. English puts the indirect object immediately after the
 * verb, so "build **me** a ..." puts a pronoun exactly where a deterministic
 * first-noun-wins parser looks for the name. Any polite phrasing hits it.
 */
object AppPromptStopWords {

    /** Pronouns. The category that produced the `me` bug. */
    private val PRONOUNS = setOf(
        "i", "me", "my", "mine", "myself",
        "you", "your", "yours", "yourself",
        "we", "us", "our", "ours", "ourselves",
        "it", "its", "itself", "them", "their", "theirs", "they",
        "he", "him", "his", "she", "her", "hers"
    )

    /** Determiners, conjunctions and prepositions. */
    private val FUNCTION_WORDS = setOf(
        "a", "an", "the", "this", "that", "these", "those",
        "and", "or", "but", "with", "without", "for", "from", "into", "onto",
        "in", "on", "at", "to", "of", "by", "as", "so", "than", "then",
        "if", "when", "where", "which", "who", "whom", "while"
    )

    /** Modals and auxiliaries. Present in almost every request, never a feature. */
    private val MODALS = setOf(
        "can", "could", "will", "would", "shall", "should", "may", "might", "must",
        "is", "are", "was", "were", "be", "been", "being", "do", "does", "did",
        "have", "has", "had", "am"
    )

    /**
     * Verbs of desire.
     *
     * The same positional trap as the pronouns: "I **want** a budget tool" puts
     * one exactly where the first-noun-wins parser looks, and it named the
     * application `want`. These are how a request is framed, never what is being
     * requested. Distinct from the build verbs in [AppActionRegistry], which are
     * already excluded — "want" is not an action the factory performs.
     */
    private val DESIRE_VERBS = setOf(
        "want", "wants", "wanted", "need", "needs", "needed",
        "wish", "wishes", "like", "likes", "prefer", "prefers",
        "let", "help", "get", "gets", "give", "gives", "try"
    )

    /** Politeness and filler. */
    private val COURTESY = setOf(
        "please", "kindly", "just", "simply", "also", "some", "any", "something",
        "thing", "things", "stuff", "etc"
    )

    /** Size and quality adjectives that describe the request, not the product. */
    private val QUALIFIERS = setOf(
        "simple", "small", "basic", "quick", "little", "tiny", "minimal",
        "local", "nice", "good", "new", "full", "complete"
    )

    /**
     * Words naming the artifact kind rather than a feature of it.
     *
     * Already consumed by [IntentParser]'s `kind` detection, so leaving them in
     * the feature list states the same fact twice.
     */
    private val ARTIFACT_KINDS = setOf(
        "cli", "web", "website", "frontend", "api", "service", "backend",
        "desktop", "android", "mobile", "app", "application", "program",
        "project", "repository", "repo", "tool", "script"
    )

    /** Words describing the deliverables rather than the behaviour. */
    private val DELIVERABLES = setOf("tests", "test", "readme", "docs", "documentation")

    /** The full vocabulary. */
    val ALL: Set<String> =
        PRONOUNS + FUNCTION_WORDS + MODALS + DESIRE_VERBS + COURTESY + QUALIFIERS +
            ARTIFACT_KINDS + DELIVERABLES

    /** True when [word] carries no application meaning. */
    fun isStopWord(word: String): Boolean = word.lowercase() in ALL

    /**
     * A name for the application, or null when the prompt names nothing.
     *
     * Null rather than a fallback, so the caller owns the default and this stays
     * a question about the prompt. A one-character token is rejected too: a bare
     * letter is never an application name, and accepting one reintroduces the
     * class of bug this exists to prevent.
     */
    fun firstMeaningful(words: List<String>, isAction: (String) -> Boolean): String? =
        words.firstOrNull { word ->
            word.length > 1 && !isStopWord(word) && !isAction(word)
        }
}
