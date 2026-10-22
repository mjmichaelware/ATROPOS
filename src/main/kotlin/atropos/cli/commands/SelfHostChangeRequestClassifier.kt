/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

/**
 * Decides whether an utterance is a request to change ATROPOS's own source.
 *
 * [SelfHostNaturalLanguageRouter] previously recognised self-host intent from a
 * fixed phrase list — "build yourself", "improve atropos", "inside out". That
 * list is a demo surface, not a capability: an operator who types the ordinary
 * form of the same request, "change the bounded process timeout in ATROPOS",
 * matched nothing and fell through to provider chat. The runtime could rebuild
 * itself only when asked in the exact words someone had thought to enumerate.
 *
 * Generalising needs two independent judgements, and collapsing them is what
 * makes naive keyword routing fail in both directions:
 *
 *  - **Subject** — is ATROPOS itself the thing being talked about? "build a
 *    calculator" names a mutation but not this codebase.
 *  - **Mood** — is this an instruction to change something, or a question about
 *    it? "how does ATROPOS build itself?" addresses the runtime and contains a
 *    build verb, yet answering it must never mutate a source file.
 *
 * Requiring both is what lets the verb list widen safely. A classifier that only
 * checked for mutation verbs would treat every question mentioning a verb as an
 * order; one that only checked the subject would self-mutate on being asked what
 * it does.
 *
 * Interrogatives are checked before verbs deliberately. English lets a question
 * contain a bare imperative ("can you add a field to X?"), so mood is decided by
 * the strongest signal present rather than by the first one found — and a
 * trailing question mark is treated as decisive, because an operator who ends a
 * line with `?` is asking.
 */
class SelfHostChangeRequestClassifier {

    fun classify(text: String): SelfHostUtterance {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return SelfHostUtterance.UNRELATED

        if (!addressesAtropos(normalized)) return SelfHostUtterance.UNRELATED
        if (isQuestion(normalized)) return SelfHostUtterance.QUESTION
        if (asksContinuation(normalized)) return SelfHostUtterance.CONTINUATION
        if (asksMutation(normalized)) return SelfHostUtterance.CHANGE_REQUEST
        return SelfHostUtterance.UNRELATED
    }

    /**
     * True when the runtime itself is the subject.
     *
     * Three ways to name it, in descending explicitness:
     *  - by name ("ATROPOS");
     *  - by naming the self-host machinery ("run self-host Phase 11") — nothing
     *    but this runtime has a self-host phase, so the phrase identifies the
     *    subject even with no name in the line;
     *  - reflexively ("build yourself"), which counts only alongside a build-ish
     *    verb because it is otherwise ambiguous — "write a script that documents
     *    itself" is not an instruction to ATROPOS.
     */
    private fun addressesAtropos(text: String): Boolean {
        if (ATROPOS_NAMES.any { it in text }) return true
        if (SELF_BUILD_PHRASES.any { it in text }) return true
        val reflexive = REFLEXIVE_NAMES.any { it in text }
        return reflexive && asksMutation(text)
    }

    private fun isQuestion(text: String): Boolean {
        if (text.endsWith("?")) return true
        val firstWord = text.substringBefore(' ').trim(',', '.', ':', ';')
        if (firstWord in LEADING_INTERROGATIVES) return true
        return INTERROGATIVE_PHRASES.any { it in text }
    }

    private fun asksContinuation(text: String): Boolean =
        CONTINUATION_VERBS.any { verb -> containsWord(text, verb) }

    private fun asksMutation(text: String): Boolean =
        MUTATION_VERBS.any { verb -> containsWord(text, verb) } ||
            SELF_BUILD_PHRASES.any { it in text }

    /**
     * Word-boundary match. Substring matching would fire "add" inside "address"
     * and "set" inside "reset", which is how a keyword router acquires false
     * positives that are almost impossible to trace back to their cause.
     */
    private fun containsWord(text: String, word: String): Boolean =
        Regex("(^|[^a-z])${Regex.escape(word)}([^a-z]|$)").containsMatchIn(text)

    private companion object {
        val ATROPOS_NAMES = listOf("atropos")
        val REFLEXIVE_NAMES = listOf("yourself", "itself", "your own", "its own")

        val SELF_BUILD_PHRASES = listOf(
            "self-host", "self host", "inside out", "inside-out",
            "build itself", "build yourself", "improve itself", "improve yourself"
        )

        val MUTATION_VERBS = listOf(
            "change", "add", "fix", "update", "modify", "remove", "delete",
            "rename", "refactor", "implement", "edit", "set", "make", "build",
            "improve", "extend", "wire", "replace", "introduce", "create",
            "harden", "bump", "rewrite", "patch", "correct", "adjust"
        )

        val CONTINUATION_VERBS = listOf("continue", "resume", "recover", "restart")

        val LEADING_INTERROGATIVES = setOf(
            "what", "why", "how", "when", "where", "who", "which",
            "is", "are", "does", "do", "did", "can", "could", "would", "should", "will"
        )

        val INTERROGATIVE_PHRASES = listOf(
            "explain ", "describe ", "tell me ", "show me ", "what is", "what does", "how does"
        )
    }
}

/** What an operator's line was asking for, once subject and mood are both known. */
enum class SelfHostUtterance {
    /** An instruction to change ATROPOS's own source. */
    CHANGE_REQUEST,

    /** A request to continue or recover an interrupted self-host run. */
    CONTINUATION,

    /** About ATROPOS, but asking rather than instructing. Must not mutate. */
    QUESTION,

    /** Not addressed to the runtime's own source at all. */
    UNRELATED
}
