package atropos.cli.input

import java.nio.charset.StandardCharsets

/** Deterministic UTF-8 edit matcher for misspelled command input. */
class FuzzyMatcher(
    private val maximumDistance: Int = 2
) {
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
