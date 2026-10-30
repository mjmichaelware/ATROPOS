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
 *
 * ## Research: Ephemeral In-Memory Encryption of Cached Paste Buffers
 * Evaluates AES-GCM or ChaCha20-Poly1305 ephemeral encryption for cached in-memory
 * paste buffers with key zeroing on commit to protect sensitive credentials, API keys,
 * or environment variables from heap dumps and memory inspection.
 */
class CollapsedPasteRegistry(
    /** A paste of at least this many lines is collapsed. */
    val lineThreshold: Int = DEFAULT_LINE_THRESHOLD,
    /** A paste of at least this many words is collapsed, however few lines. */
    val wordThreshold: Int = DEFAULT_WORD_THRESHOLD,
    /**
     * The most text held across all placeholders on one line.
     *
     * A bound rather than none because this is a per-session buffer fed by an
     * operator who can paste repeatedly. Past it, the paste is inserted whole:
     * degrading to the old behaviour is worse than collapsing, and losing the
     * text is worse than both.
     */
    val maxRetainedChars: Int = DEFAULT_MAX_RETAINED_CHARS
) {
    data class PasteToken(
        val id: String,
        val lineCount: Int,
        val wordCount: Int,
        val display: String
    )

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
        val trimmed = text.trimStart()
        // Explicit CLI commands (/... or !...) must never be collapsed into placeholders
        // so the operator can review, edit, and see the full command structure before execution.
        if (trimmed.startsWith("/") || trimmed.startsWith("!")) return false
        if (retained + text.length > maxRetainedChars) return false
        return lineCount(text) >= lineThreshold || wordCount(text) >= wordThreshold
    }

    /**
     * Stores [text] and returns the placeholder to put in the line.
     *
     * The caller is expected to have asked [shouldCollapse] first; storing a
     * short paste is permitted and simply produces a placeholder nobody wanted.
     */
    fun collapse(text: String): String = registerPaste(text).display

    fun registerPaste(text: String): PasteToken {
        val lines = lineCount(text)
        val words = wordCount(text)
        val pill = renderPill(nextToken, lines, words)
        val token = PasteToken(id = nextToken.toString(), lineCount = lines, wordCount = words, display = pill)
        nextToken += 1
        stored[pill] = text
        retained += text.length
        return token
    }

    fun getPaste(tokenOrDisplay: String): String? = stored[tokenOrDisplay]

    fun renderPill(id: Int, lines: Int, words: Int): String =
        "[#$id pasted ${plural(lines, "line")}, ${plural(words, "word")}]"

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
