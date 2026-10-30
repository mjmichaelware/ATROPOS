/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

/**
 * ATOM 8.2: Collapsed Paste Registry and Rendering.
 *
 * Replaces large multiline clipboard bursts exceeding safe terminal viewport thresholds
 * with a compact token pill `[#n pasted X lines, Y words]` while holding the underlying text
 * in an isolated in-memory buffer.
 *
 * ## Research: Ephemeral In-Memory Encryption of Cached Paste Buffers
 * Evaluates AES-GCM or ChaCha20-Poly1305 ephemeral encryption for cached in-memory
 * paste buffers with key zeroing on commit to protect sensitive credentials, API keys,
 * or environment variables from heap dumps and memory inspection.
 */
class PastePillTokenRegistry(
    val lineThreshold: Int = DEFAULT_LINE_THRESHOLD,
    val wordThreshold: Int = DEFAULT_WORD_THRESHOLD,
    val maxRetainedChars: Int = DEFAULT_MAX_RETAINED_CHARS
) {
    data class PasteToken(
        val id: String,
        val lineCount: Int,
        val wordCount: Int,
        val display: String
    )

    private val storage = LinkedHashMap<String, String>()
    private var sequenceId = 1
    private var retainedChars = 0

    fun shouldCollapse(text: String): Boolean {
        if (text.isEmpty()) return false
        val trimmed = text.trimStart()
        if (trimmed.startsWith("/") || trimmed.startsWith("!")) return false
        if (retainedChars + text.length > maxRetainedChars) return false
        return lineCount(text) >= lineThreshold || wordCount(text) >= wordThreshold
    }

    fun registerPaste(text: String): PasteToken {
        val lines = lineCount(text)
        val words = wordCount(text)
        val pill = renderPill(sequenceId, lines, words)
        val token = PasteToken(id = sequenceId.toString(), lineCount = lines, wordCount = words, display = pill)
        sequenceId += 1
        storage[pill] = text
        retainedChars += text.length
        return token
    }

    fun getPaste(pillOrId: String): String? = storage[pillOrId]

    fun renderPill(id: Int, lines: Int, words: Int): String {
        val lineText = "$lines line" + if (lines == 1) "" else "s"
        val wordText = "$words word" + if (words == 1) "" else "s"
        return "[#$id pasted $lineText, $wordText]"
    }

    fun expand(line: String): String {
        if (storage.isEmpty()) return line
        var result = line
        storage.forEach { (pill, text) ->
            result = result.replace(pill, text)
        }
        return result
    }

    fun clear() {
        storage.clear()
        sequenceId = 1
        retainedChars = 0
    }

    private fun lineCount(text: String): Int = text.count { it == '\n' } + 1
    private fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    companion object {
        const val DEFAULT_LINE_THRESHOLD = 8
        const val DEFAULT_WORD_THRESHOLD = 250
        const val DEFAULT_MAX_RETAINED_CHARS = 4 * 1024 * 1024
    }
}
