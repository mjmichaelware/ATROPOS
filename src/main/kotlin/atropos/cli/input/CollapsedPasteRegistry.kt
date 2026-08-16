/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * Keeps a large paste out of the prompt line while keeping it in the request.
 *
 * Pasting a Source Document into the terminal used to scroll the whole thing
 * through the composer: three thousand words of it, redrawn on every keystroke
 * after, with the operator's own question somewhere above the fold. The text
 * has to arrive intact — it is the entire point of pasting it — but it does not
 * have to be *shown*.
 *
 * So the line holds a placeholder and this holds the text. The placeholder is
 * ordinary characters in the buffer, which is what makes the behaviour
 * predictable: it can be moved, and deleting it deletes the paste, because to
 * an operator looking at `[#1 pasted 412 lines, 3081 words]` that is plainly
 * what deleting it should do.
 *
 * ## Why the registry is cleared on commit
 *
 * A placeholder that outlived its line could be recalled from history into a
 * session where its text no longer existed, and would then expand to nothing —
 * silently sending a prompt with the document missing, which is the failure
 * this whole mechanism exists to prevent. Expansion happens at commit and the
 * store empties, so a placeholder never refers to text that is gone.
 */
class CollapsedPasteRegistry(
    /** A paste of at least this many lines is collapsed. */
    private val lineThreshold: Int = DEFAULT_LINE_THRESHOLD,
    /** A paste of at least this many words is collapsed, however few lines. */
    private val wordThreshold: Int = DEFAULT_WORD_THRESHOLD,
    /**
     * The most text held across all placeholders on one line.
     *
     * A bound rather than none because this is a per-session buffer fed by an
     * operator who can paste repeatedly. Past it, the paste is inserted whole:
     * degrading to the old behaviour is worse than collapsing, and losing the
     * text is worse than both.
     */
    private val maxRetainedChars: Int = DEFAULT_MAX_RETAINED_CHARS
) {
    private val stored = LinkedHashMap<String, String>()
    private var retained = 0
    private var nextToken = 1

    /**
     * Whether [text] is large enough to be worth hiding.
     *
     * Two thresholds because one line of three thousand words and forty lines
     * of six are both unreadable in a composer, and neither measure catches the
     * other.
     */
    fun shouldCollapse(text: String): Boolean {
        if (text.isEmpty()) return false
        if (retained + text.length > maxRetainedChars) return false
        return lineCount(text) >= lineThreshold || wordCount(text) >= wordThreshold
    }

    /**
     * Stores [text] and returns the placeholder to put in the line.
     *
     * The caller is expected to have asked [shouldCollapse] first; storing a
     * short paste is permitted and simply produces a placeholder nobody wanted.
     */
    fun collapse(text: String): String {
        val token = "[#$nextToken pasted ${plural(lineCount(text), "line")}, ${plural(wordCount(text), "word")}]"
        nextToken += 1
        stored[token] = text
        retained += text.length
        return token
    }

    /** Every placeholder currently standing in for text, in the order minted. */
    fun placeholders(): List<String> = stored.keys.toList()

    /**
     * Replaces placeholders in [line] with the text they stand for.
     *
     * A placeholder the operator deleted simply does not appear, and its text
     * is dropped with it — which is what deleting a visible summary of a paste
     * means. Nothing is appended to rescue it: a document reappearing in a
     * prompt after the operator removed it would be worse than losing it.
     */
    fun expand(line: String): String {
        if (stored.isEmpty()) return line
        var expanded = line
        stored.forEach { (token, text) -> expanded = expanded.replace(token, text) }
        return expanded
    }

    /** True when a line still refers to stored text. */
    fun hasCollapsedText(line: String): Boolean = stored.keys.any { it in line }

    fun clear() {
        stored.clear()
        retained = 0
        nextToken = 1
    }

    private fun lineCount(text: String): Int = text.count { it == '\n' } + 1

    private fun wordCount(text: String): Int =
        text.split(WHITESPACE).count { it.isNotBlank() }

    private fun plural(count: Int, noun: String): String =
        "$count $noun" + if (count == 1) "" else "s"

    private companion object {
        const val DEFAULT_LINE_THRESHOLD = 8
        const val DEFAULT_WORD_THRESHOLD = 250
        const val DEFAULT_MAX_RETAINED_CHARS = 4 * 1024 * 1024
        val WHITESPACE = Regex("\\s+")
    }
}
