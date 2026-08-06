package atropos.core.memory

import java.security.MessageDigest

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
    private val overlapTokens = (windowTokens * overlapPercent / 100).coerceIn(0, windowTokens - 1)

    init {
        require(windowTokens > 0) { "chunk window must be positive" }
        require(overlapPercent in 0..99) { "chunk overlap must be between 0 and 99 percent" }
    }

    fun chunk(source: String): List<MemorySourceChunk> {
        val tokens = TOKEN_PATTERN.findAll(source).toList()
        if (tokens.isEmpty()) return emptyList()

        val step = (windowTokens - overlapTokens).coerceAtLeast(1)
        val chunks = mutableListOf<MemorySourceChunk>()
        var start = 0
        var index = 0
        while (start < tokens.size) {
            val end = (start + windowTokens).coerceAtMost(tokens.size)
            val text = source.substring(tokens[start].range.first, tokens[end - 1].range.last + 1)
            chunks += MemorySourceChunk(
                index = index,
                tokenStart = start,
                tokenEndExclusive = end,
                text = text,
                sha256 = sha256(text)
            )
            if (end == tokens.size) break
            start += step
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
