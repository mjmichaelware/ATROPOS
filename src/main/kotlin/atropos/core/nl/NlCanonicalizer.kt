/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.nl

import java.text.Normalizer

/**
 * Dirty natural language in, canonical bytes out. No model call.
 *
 * `SUP.NL.BYTE-CANONICAL-FORM`: "Dirty NL becomes deterministic input without
 * paying a model call; parse failures become reproducible. Competitors either
 * reject dirty input or spend tokens to clean it."
 *
 * Source Doc 5 asks whether this needs a small trained model. It does not, and
 * the reason is worth stating: the problems that actually arrive from a phone
 * keyboard are *encoding* problems, not *language* problems. Smart quotes from
 * autocorrect, a zero-width joiner left by an emoji, `ﬁ` as one codepoint,
 * three spaces where a swipe hesitated, a stray control character from a paste
 * — every one of these is a byte-level defect with a single correct answer,
 * and a model would give a probabilistic answer to a deterministic question.
 *
 * What is *not* done here matters as much. Spelling is not corrected;
 * `atropos buld the thing` comes out unchanged except for spacing, because a
 * canonicalizer that guessed at words would silently change what the operator
 * asked for. Misspellings belong to [atropos.cli.input.FuzzyMatcher], which
 * matches against a known command set and can therefore be wrong safely — it
 * proposes, and the operator confirms.
 *
 * Every rule below is idempotent: canonicalizing a canonical string returns it
 * unchanged. Without that, re-processing input at a second entry point would
 * keep changing it, and no hash of the result would mean anything.
 */
class NlCanonicalizer(private val maxChars: Int = DEFAULT_MAX_CHARS) {

    fun canonicalize(dirty: String): NlCanonicalResult {
        val notes = mutableListOf<String>()
        var text = dirty

        if (text.length > maxChars) {
            // Bounded before any per-character work. A megabyte pasted into a
            // phone prompt should cost one substring, not a full scan.
            text = text.take(maxChars)
            notes += "truncated to $maxChars characters"
        }

        // NFKC folds compatibility forms: ligatures, full-width Latin, and the
        // typographic quotes autocorrect produces. Chosen over NFC because the
        // goal is one representation per intent, and `＂` versus `"` is a
        // difference no downstream stage should have to know about.
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        if (normalized != text) notes += "unicode normalized (NFKC)"
        text = normalized

        val straightened = foldTypographic(text)
        if (straightened != text) notes += "straightened typographic punctuation"
        text = straightened

        val stripped = stripInvisible(text)
        if (stripped != text) notes += "removed invisible characters"
        text = stripped

        val collapsed = collapseWhitespace(text)
        if (collapsed != text) notes += "collapsed whitespace"
        text = collapsed

        return NlCanonicalResult(
            original = dirty,
            canonical = text,
            notes = notes,
            recoverable = text.isNotBlank()
        )
    }

    /**
     * Folds the punctuation autocorrect substitutes.
     *
     * NFKC does not do this and is right not to: `“` is a distinct character
     * from `"`, not a compatibility form of it, and folding it would be wrong
     * for prose. It is right *here* because this text is about to be matched
     * against paths, flags and command names, where a curly quote is never
     * what the operator meant — a phone that helpfully turned
     * `"src/main"` into `“src/main”` has produced a path that does not exist.
     *
     * The en and em dashes fold for the same reason: `--verbose` typed on a
     * phone frequently arrives as `–verbose`.
     */
    private fun foldTypographic(text: String): String = buildString(text.length) {
        text.forEach { character ->
            append(TYPOGRAPHIC[character] ?: character)
        }
    }

    /**
     * Removes characters that carry no meaning a reader can see.
     *
     * Control characters, zero-width joiners and non-breaking spaces reach here
     * from pastes and emoji sequences. They are invisible, so an operator
     * comparing two prompts that differ only by one sees identical text and
     * cannot explain why they behaved differently.
     *
     * Tab and newline survive: they are structure, not noise, and a pasted
     * multi-line prompt loses its shape without them.
     */
    private fun stripInvisible(text: String): String = buildString(text.length) {
        text.forEach { character ->
            when {
                character == '\n' || character == '\t' -> append(character)
                character == ' ' -> append(' ')
                character.isISOControl() -> Unit
                character in INVISIBLE -> Unit
                else -> append(character)
            }
        }
    }

    /**
     * Collapses runs of spaces and trims each line.
     *
     * A swipe keyboard emits doubled spaces where the finger paused, and a
     * prompt typed across a bumpy commute accumulates them. Blank lines are
     * preserved as at most one, because a deliberate paragraph break is
     * structure and three blank lines are not.
     */
    private fun collapseWhitespace(text: String): String {
        val lines = text.lines().map { line ->
            line.replace(SPACE_RUN, " ").trim()
        }
        val out = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty() && out.lastOrNull()?.isEmpty() == true) continue
            out += line
        }
        return out.joinToString("\n").trim()
    }

    private companion object {
        /**
         * A prompt ceiling, not a document ceiling. Anything longer is a file,
         * and files arrive by `@mention` where they are territory-checked.
         */
        const val DEFAULT_MAX_CHARS = 32_000

        val SPACE_RUN = Regex(" {2,}")

        /** What autocorrect substitutes, and what it displaced. */
        val TYPOGRAPHIC: Map<Char, Char> = mapOf(
            '“' to '"', '”' to '"', '„' to '"', '‟' to '"',
            '‘' to '\'', '’' to '\'', '‚' to '\'', '‛' to '\'',
            '–' to '-', '—' to '-', '−' to '-'
        )

        /** Zero-width and directional marks: invisible, and never meaningful here. */
        val INVISIBLE = setOf(
            '​', '‌', '‍', '﻿',
            '‎', '‏', '⁠'
        )
    }
}

/**
 * @param notes what was changed, in the operator's terms. Present so a prompt
 *   that behaved unexpectedly can be explained without re-deriving the
 *   transformation by hand.
 * @param recoverable false when nothing survived canonicalization. Input that
 *   was entirely invisible characters is not an empty prompt — it is a prompt
 *   whose content was never real, and the two deserve different messages.
 */
data class NlCanonicalResult(
    val original: String,
    val canonical: String,
    val notes: List<String>,
    val recoverable: Boolean
) {
    val changed: Boolean get() = original != canonical

    fun render(): String =
        if (!changed) "input was already canonical"
        else "canonicalized: ${notes.joinToString("; ")}"
}
