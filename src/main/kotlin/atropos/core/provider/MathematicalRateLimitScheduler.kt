/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ExecutionSnapshot(
    val attemptNumber: Int,
    val output: String = "",
    val byteCount: Int = output.toByteArray(Charsets.UTF_8).size,
    val validLines: Int = MathematicalRateLimitScheduler.countValidLines(output),
    val astDepth: Int = MathematicalRateLimitScheduler.computeAstDepth(output),
    val error: String? = null
)

data class MathematicalProgressDelta(
    val deltaBytes: Int,
    val deltaLines: Int,
    val deltaAstDepth: Int,
    val hasProgress: Boolean,
    val progressFormulaSummary: String
)

/**
 * Mathematical rate-limit backoff scheduler and 3-strike progress evaluator.
 * Implements strict HTTP 429/OTPM retry-after extraction with a +10s buffer,
 * and computes delta AST depth, byte completion, and valid line progress
 * to determine provider retry eligibility before cascading.
 */
object MathematicalRateLimitScheduler {
    const val BUFFER_SECONDS = 10L
    const val MAX_STRIKES = 3

    /** Pluggable sleep action for testability without slowing unit test suites. */
    var sleeper: (Long) -> Unit = { millis ->
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Extracts the retry-after delta in seconds from HTTP headers or error body.
     * Evaluates headers: Retry-After, x-ratelimit-reset-requests, retry-after-ms.
     * Falls back to textual extraction if headers are empty.
     */
    fun parseRetryAfterSeconds(
        headers: Map<String, List<String>> = emptyMap(),
        errorBody: String = ""
    ): Long {
        // 1. Search HTTP headers case-insensitively
        val headerMatch = headers.entries.firstOrNull { (k, _) ->
            k.equals("retry-after", ignoreCase = true) ||
            k.equals("x-ratelimit-reset-requests", ignoreCase = true) ||
            k.equals("x-ratelimit-reset-tokens", ignoreCase = true) ||
            k.equals("retry-after-ms", ignoreCase = true)
        }

        if (headerMatch != null) {
            val key = headerMatch.key.lowercase(Locale.US)
            val rawVal = headerMatch.value.firstOrNull()?.trim().orEmpty()

            if (rawVal.isNotBlank()) {
                if (key.contains("ms")) {
                    val ms = rawVal.toLongOrNull()
                    if (ms != null) return Math.ceil(ms / 1000.0).toLong().coerceAtLeast(1L)
                }

                rawVal.toLongOrNull()?.let { return it.coerceAtLeast(1L) }

                val cleanNum = rawVal.removeSuffix("s").removeSuffix("sec").trim()
                cleanNum.toDoubleOrNull()?.let { return Math.ceil(it).toLong().coerceAtLeast(1L) }

                // Check HTTP RFC 1123 date
                runCatching {
                    val date = DateTimeFormatter.RFC_1123_DATE_TIME.parse(rawVal, Instant::from)
                    val diff = Duration.between(Instant.now(), date).seconds
                    if (diff > 0) return diff
                }
            }
        }

        // 2. Fallback: Parse from error message or response body
        if (errorBody.isNotBlank()) {
            val bodyRegex = Regex(
                """(?:try again in|retry after|retry-after[:\s]+|wait[:\s]+)\s*([0-9]+(?:\.[0-9]+)?)\s*(s|sec|seconds|ms)?""",
                RegexOption.IGNORE_CASE
            )
            val match = bodyRegex.find(errorBody)
            if (match != null) {
                val num = match.groupValues[1].toDoubleOrNull() ?: 5.0
                val unit = match.groupValues[2].lowercase(Locale.US)
                val sec = if (unit == "ms") num / 1000.0 else num
                return Math.ceil(sec).toLong().coerceAtLeast(1L)
            }
        }

        // Default fallback for 429 when no explicit duration is given
        return 5L
    }

    /**
     * Computes the exact sleep duration including the mandatory 10-second buffer.
     */
    fun computeSleepDurationSeconds(retryAfterSeconds: Long): Long =
        retryAfterSeconds.coerceAtLeast(1L) + BUFFER_SECONDS

    /**
     * Executes the backoff sleep with the 10-second buffer.
     */
    fun sleepWithBuffer(
        retryAfterSeconds: Long,
        onSleep: (totalSeconds: Long) -> Unit = {}
    ): Long {
        val totalSec = computeSleepDurationSeconds(retryAfterSeconds)
        onSleep(totalSec)
        val millis = totalSec * 1000L
        sleeper(millis)
        return millis
    }

    /**
     * Counts valid code lines (excluding blank lines, markdown code fences, and comments).
     */
    fun countValidLines(code: String): Int =
        code.lineSequence().count { rawLine ->
            val trimmed = rawLine.trim()
            trimmed.isNotBlank() &&
                !trimmed.startsWith("```") &&
                !trimmed.startsWith("//") &&
                !trimmed.startsWith("#") &&
                !trimmed.startsWith("/*") &&
                !trimmed.startsWith("*")
        }

    /**
     * Computes maximum nesting depth for AST blocks ({, (, [).
     */
    fun computeAstDepth(code: String): Int {
        var maxDepth = 0
        var currentDepth = 0
        for (ch in code) {
            when (ch) {
                '{', '(', '[' -> {
                    currentDepth++
                    if (currentDepth > maxDepth) maxDepth = currentDepth
                }
                '}', ')', ']' -> {
                    if (currentDepth > 0) currentDepth--
                }
            }
        }
        return maxDepth
    }

    /**
     * Evaluates whether mathematical progress was made between attempt 1 and attempt 2.
     * Progress is detected if delta bytes > 0 OR delta lines > 0 OR delta AST depth > 0.
     */
    fun computeProgress(
        previous: ExecutionSnapshot,
        current: ExecutionSnapshot
    ): MathematicalProgressDelta {
        val deltaBytes = current.byteCount - previous.byteCount
        val deltaLines = current.validLines - previous.validLines
        val deltaAst = current.astDepth - previous.astDepth

        val hasProgress = deltaBytes > 0 || deltaLines > 0 || deltaAst > 0
        val summary = "Δbytes=$deltaBytes, Δlines=$deltaLines, Δast=$deltaAst -> progress=$hasProgress"

        return MathematicalProgressDelta(
            deltaBytes = deltaBytes,
            deltaLines = deltaLines,
            deltaAstDepth = deltaAst,
            hasProgress = hasProgress,
            progressFormulaSummary = summary
        )
    }
}
