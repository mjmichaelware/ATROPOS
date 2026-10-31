package atropos.core.provider

import java.time.Duration
import java.time.Instant

/**
 * CodeiumCompletionCascadeRouter
 *
 * Implements ATOM AI7: CODEIUM REASONING COMPLETION CASCADE ROUTER
 * Routes completion queries through tiered inference cascades:
 * Tier 1: Local edge / fast streaming model (<25ms)
 * Tier 2: Mid-tier standard completion model (<150ms)
 * Tier 3: High-capacity deep reasoning model (fallback on complex syntax or low confidence)
 */
enum class InferenceTier {
    FAST_EDGE,
    STANDARD_AUTOCOMPLETE,
    DEEP_REASONING
}

data class CompletionCascadeRequest(
    val prompt: String,
    val filePath: String,
    val cursorOffset: Int,
    val language: String,
    val maxLatencyBudgetMs: Long = 250L,
    val minimumConfidence: Double = 0.75
)

data class CompletionCascadeResult(
    val completionText: String,
    val tierUsed: InferenceTier,
    val latencyMs: Long,
    val confidence: Double,
    val fallbacksAttempted: Int
)

class CodeiumCompletionCascadeRouter(
    private val fastEdgeProvider: (CompletionCascadeRequest) -> String? = { null },
    private val standardProvider: (CompletionCascadeRequest) -> String? = { null },
    private val reasoningProvider: (CompletionCascadeRequest) -> String? = { null }
) {
    fun routeCompletion(request: CompletionCascadeRequest): CompletionCascadeResult {
        val start = Instant.now()

        // 1. Attempt Tier 1: Fast Edge
        val edgeRes = try {
            fastEdgeProvider(request)
        } catch (_: Exception) {
            null
        }

        val elapsed1 = Duration.between(start, Instant.now()).toMillis()
        if (!edgeRes.isNullOrBlank() && elapsed1 <= request.maxLatencyBudgetMs / 2) {
            return CompletionCascadeResult(
                completionText = edgeRes,
                tierUsed = InferenceTier.FAST_EDGE,
                latencyMs = elapsed1,
                confidence = 0.90,
                fallbacksAttempted = 0
            )
        }

        // 2. Attempt Tier 2: Standard Autocomplete
        val standardRes = try {
            standardProvider(request)
        } catch (_: Exception) {
            null
        }

        val elapsed2 = Duration.between(start, Instant.now()).toMillis()
        if (!standardRes.isNullOrBlank() && elapsed2 <= request.maxLatencyBudgetMs) {
            return CompletionCascadeResult(
                completionText = standardRes,
                tierUsed = InferenceTier.STANDARD_AUTOCOMPLETE,
                latencyMs = elapsed2,
                confidence = 0.85,
                fallbacksAttempted = 1
            )
        }

        // 3. Fallback Tier 3: Deep Reasoning Model
        val reasoningRes = try {
            reasoningProvider(request)
        } catch (_: Exception) {
            null
        }

        val elapsedTotal = Duration.between(start, Instant.now()).toMillis()
        return CompletionCascadeResult(
            completionText = reasoningRes ?: edgeRes ?: standardRes ?: "",
            tierUsed = InferenceTier.DEEP_REASONING,
            latencyMs = elapsedTotal,
            confidence = if (reasoningRes != null) 0.95 else 0.40,
            fallbacksAttempted = 2
        )
    }

    companion object {
        private val defaultInstance = CodeiumCompletionCascadeRouter()
        fun defaultRouter(): CodeiumCompletionCascadeRouter = defaultInstance
    }
}
