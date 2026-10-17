package atropos.core.supremacy

import java.nio.charset.StandardCharsets
import kotlin.math.ln

data class ContextChunk(
    val path: String,
    val content: String,
    val tokenEstimate: Int,
    val shannonEntropy: Double,
    val taskRelevanceScore: Double
)

data class AllocatedContext(
    val selectedChunks: List<ContextChunk>,
    val prunedCount: Int,
    val totalTokens: Int,
    val averageEntropy: Double
)

class ContextBudgetEngine(
    private val tokenCeiling: Int = 28_000,
    private val minEntropyThresholdBitsPerByte: Double = 1.2
) {
    fun calculateShannonEntropy(bytes: ByteArray): Double {
        if (bytes.isEmpty()) return 0.0
        val frequencies = IntArray(256)
        for (b in bytes) {
            frequencies[b.toInt() and 0xFF]++
        }
        val len = bytes.size.toDouble()
        val ln2 = ln(2.0)
        var entropy = 0.0
        for (count in frequencies) {
            if (count > 0) {
                val p = count / len
                entropy -= p * (ln(p) / ln2)
            }
        }
        return entropy
    }

    fun filterAndAllocate(taskPrompt: String, candidates: Map<String, String>): AllocatedContext {
        val keywords = taskPrompt.lowercase().split(Regex("[^a-zA-Z0-9_]")).filter { it.length > 2 }.toSet()
        val chunks = candidates.map { (path, text) ->
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            val entropy = calculateShannonEntropy(bytes)
            val tokens = (text.length + 3) / 4
            val lower = text.lowercase()
            val matches = keywords.count { lower.contains(it) }
            val relevance = if (keywords.isEmpty()) 1.0 else matches.toDouble() / keywords.size.toDouble()
            ContextChunk(path, text, tokens, entropy, relevance)
        }

        val viableChunks = chunks.filter { it.shannonEntropy >= minEntropyThresholdBitsPerByte }
        val sortedChunks = viableChunks.sortedWith(
            compareByDescending<ContextChunk> { it.taskRelevanceScore * (it.shannonEntropy / 8.0) }
                .thenBy { it.tokenEstimate }
        )

        val selected = mutableListOf<ContextChunk>()
        var tokensUsed = 0
        for (chunk in sortedChunks) {
            if (tokensUsed + chunk.tokenEstimate <= tokenCeiling) {
                selected.add(chunk)
                tokensUsed += chunk.tokenEstimate
            }
        }

        val avgEntropy = if (selected.isEmpty()) 0.0 else selected.map { it.shannonEntropy }.average()
        return AllocatedContext(
            selectedChunks = selected,
            prunedCount = candidates.size - selected.size,
            totalTokens = tokensUsed,
            averageEntropy = avgEntropy
        )
    }
}
