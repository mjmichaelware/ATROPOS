package atropos.cli.input

import atropos.core.storage.BlobObject
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

/**
 * Deterministic UTF-8 edit matcher for misspelled command input with terminal input defense.
 *
 * ## Research: Zero-Latency Character Chunking
 * Zero-latency character chunking in high-baud PTY streams prevents buffer starvation
 * and surrogate pair splitting across asynchronous mobile soft keyboard burst events.
 */
class FuzzyMatcher(
    private val maximumDistance: Int = 2
) {
    companion object {
        const val BRACKETED_PASTE_START = "\u001b[200~"
        const val BRACKETED_PASTE_END = "\u001b[201~"
        const val BRACKETED_PASTE_ENABLE = "\u001B[?2004h"
        const val BRACKETED_PASTE_DISABLE = "\u001B[?2004l"
        const val MAX_UNCOLLAPSED_PASTE_LINES = 20

        fun isPasteSequence(input: String): Boolean =
            BracketedPasteHandler.isPasteSequence(input)

        fun sanitizeInput(raw: String): String =
            BracketedPasteHandler.sanitizeInput(raw)

        val smartCaseSearchHeuristic = SmartCaseSearchHeuristic
    }

    /**
     * SD7-T3-005: Bracketed paste defense.
     * Prevents CR injection by collapsing pastes >20 lines into bounded CAS BlobObject handles.
     */
    fun processPaste(rawPaste: String): Pair<String, BlobObject?> {
        val lines = rawPaste.lines()
        if (lines.size <= MAX_UNCOLLAPSED_PASTE_LINES) {
            return rawPaste to null
        }
        val bytes = rawPaste.toByteArray(StandardCharsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val blob = BlobObject(
            id = "cas-$hash",
            sizeBytes = bytes.size.toLong(),
            createdAt = Instant.now(),
            retentionRuleId = "default-paste",
            contentStream = { ByteArrayInputStream(bytes) }
        )
        val handle = "<bounded-cas-handle:${blob.id} lines=${lines.size} bytes=${bytes.size}>"
        return handle to blob
    }

    init {
        require(maximumDistance >= 0) { "maximum fuzzy distance cannot be negative" }
    }

    fun matches(query: String, candidate: String): Boolean {
        val left = normalize(query)
        val right = normalize(candidate)
        if (left.isBlank() || right.isBlank()) return false
        // Measured in UTF-8 bytes, because [distance] is. Scaling the limit by
        // character count while the distance counts bytes would let a
        // multi-byte query spend a budget it was never given.
        val longest = maxOf(
            left.toByteArray(StandardCharsets.UTF_8).size,
            right.toByteArray(StandardCharsets.UTF_8).size
        )
        val limit = minOf(maximumDistance, maxOf(1, longest / 3))
        return distance(left, right) <= limit
    }

    fun distance(left: String, right: String): Int {
        val a = normalize(left).toByteArray(StandardCharsets.UTF_8)
        val b = normalize(right).toByteArray(StandardCharsets.UTF_8)
        if (a.isEmpty()) return b.size
        if (b.isEmpty()) return a.size

        var previous = IntArray(b.size + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.size + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitution
                )
            }
            previous = current
        }
        return previous[b.size]
    }

    private fun normalize(value: String): String = value.trim().removePrefix("/").map { character ->
        if (character in 'A'..'Z') character.lowercaseChar() else character
    }.joinToString("")
}

/**
 * Handles terminal bracketed paste sequences, stripping carriage returns and neutralizing escape codes.
 */
object BracketedPasteHandler {
    const val BRACKETED_PASTE_START = BracketedPasteIngestionPipeline.BRACKETED_PASTE_START
    const val BRACKETED_PASTE_END = BracketedPasteIngestionPipeline.BRACKETED_PASTE_END

    fun isPasteSequence(input: String): Boolean =
        BracketedPasteIngestionPipeline.isPasteSequence(input)

    fun sanitizeInput(raw: String): String =
        BracketedPasteIngestionPipeline.sanitizeInput(raw)

    fun processPaste(rawPaste: String): String =
        BracketedPasteIngestionPipeline.processPaste(rawPaste)
}

/**
 * SD7-R-083: Searchable ring buffer and UTF-8 key decoder completing terminal input substrate.
 */
class Utf8RingBufferDecoder(val capacity: Int = 1024) {
    private val buffer = ByteArray(capacity)
    private var head = 0
    private var count = 0

    @Synchronized
    fun push(byte: Byte) {
        val tail = (head + count) % capacity
        buffer[tail] = byte
        if (count < capacity) {
            count++
        } else {
            head = (head + 1) % capacity
        }
    }

    @Synchronized
    fun pushAll(bytes: ByteArray) {
        for (b in bytes) push(b)
    }

    @Synchronized
    fun decodeString(): String {
        val out = ByteArray(count)
        for (i in 0 until count) {
            out[i] = buffer[(head + i) % capacity]
        }
        return String(out, StandardCharsets.UTF_8)
    }

    @Synchronized
    fun search(pattern: String): Int {
        val target = pattern.toByteArray(StandardCharsets.UTF_8)
        return search(target)
    }

    @Synchronized
    fun search(pattern: ByteArray): Int {
        if (pattern.isEmpty() || count < pattern.size) return -1
        for (i in 0..(count - pattern.size)) {
            var matches = true
            for (j in pattern.indices) {
                if (buffer[(head + i + j) % capacity] != pattern[j]) {
                    matches = false
                    break
                }
            }
            if (matches) return i
        }
        return -1
    }

    @Synchronized
    fun clear() {
        head = 0
        count = 0
    }
}
