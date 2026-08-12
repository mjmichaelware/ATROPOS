package atropos.core.memory

import java.security.MessageDigest
import java.util.ArrayDeque

/** Deterministically splits source text into bounded, overlapping token windows. */
data class MemorySourceChunk(
    val index: Int,
    val tokenStart: Int,
    val tokenEndExclusive: Int,
    val text: String,
    val sha256: String
)

class MemorySourceChunker(
    private val windowTokens: Int = DEFAULT_WINDOW_TOKENS,
    overlapPercent: Int = DEFAULT_OVERLAP_PERCENT
) {
    private val overlapTokens = run {
        require(windowTokens > 0) { "chunk window must be positive" }
        require(overlapPercent in 0..99) { "chunk overlap must be between 0 and 99 percent" }
        (windowTokens * overlapPercent / 100).coerceIn(0, windowTokens - 1)
    }

    fun chunk(source: String): List<MemorySourceChunk> {
        val step = (windowTokens - overlapTokens).coerceAtLeast(1)
        val chunks = mutableListOf<MemorySourceChunk>()
        val tokenRanges = TOKEN_PATTERN.findAll(source).map { it.range }.iterator()
        val window = ArrayDeque<IntRange>(windowTokens)
        var tokenStart = 0
        var index = 0
        while (true) {
            var addedTokens = 0
            while (window.size < windowTokens && tokenRanges.hasNext()) {
                window.addLast(tokenRanges.next())
                addedTokens++
            }
            if (window.isEmpty() || (index > 0 && addedTokens == 0)) break

            val first = window.peekFirst()
            val last = window.peekLast()
            val end = tokenStart + window.size
            val text = source.substring(first.first, last.last + 1)
            chunks += MemorySourceChunk(
                index = index,
                tokenStart = tokenStart,
                tokenEndExclusive = end,
                text = text,
                sha256 = sha256(text)
            )
            if (window.size < windowTokens) break
            repeat(step) { window.removeFirst() }
            tokenStart += step
            index++
        }
        return chunks
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DEFAULT_WINDOW_TOKENS = 1_024
        const val DEFAULT_OVERLAP_PERCENT = 10
        val TOKEN_PATTERN = Regex("\\S+")
    }
}
