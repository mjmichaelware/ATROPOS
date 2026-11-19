/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.util.LinkedHashMap

/**
 * ATOM 8.2: Bracketed Paste Demuxing and Large Paste Management.
 *
 * Keeps large multiline paste events out of the terminal prompt line while keeping them
 * in the active request payload. Replaces large pastes with interactive single-line token
 * placeholders (e.g. `[#1 pasted 412 lines, 3081 words]`) to preserve viewport space.
 * Allows the operator to expand, edit, or submit pasted content seamlessly.
 *
 * ## Research: Memory-Mapped Temporary Storage Backing
 * Researches memory-mapped temporary storage (mmap / ByteBuffer) backing for multi-megabyte
 * clipboard insertions to prevent JVM garbage collection spikes and out-of-memory errors
 * on low-memory mobile hardware.
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

    val pillRegistry: PastePillTokenRegistry = PastePillTokenRegistry(lineThreshold, wordThreshold, maxRetainedChars)

    private val stored: LinkedHashMap<String, String> = LinkedHashMap()
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

    /** Returns the underlying map of token to pasted text. */
    fun activeTokens(): Map<String, String> = LinkedHashMap(stored)

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

    companion object {
        const val DEFAULT_LINE_THRESHOLD = 8
        const val DEFAULT_WORD_THRESHOLD = 250
        const val DEFAULT_MAX_RETAINED_CHARS = 4 * 1024 * 1024
        val WHITESPACE = Regex("\\s+")
    }
}
