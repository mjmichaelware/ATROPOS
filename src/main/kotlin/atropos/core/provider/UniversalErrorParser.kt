/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

/**
 * Universal error parser for ATROPOS provider pipeline.
 *
 * Intercepts raw network exceptions (Timeouts, ConnectExceptions) and parses
 * JSON error bodies of HTTP responses (extracting error.message and error.type).
 */
object UniversalErrorParser {

    /** Parse any error object (Throwable, String, or other). */
    fun parseAny(error: Any?): ParsedUniversalError = when (error) {
        is Throwable -> parse(error)
        is String -> parse(error)
        null -> ParsedUniversalError(UniversalErrorCategory.UNKNOWN, "Null error encountered")
        else -> parse(error.toString())
    }

    /** Parse raw network exceptions and embedded response messages. */
    fun parse(throwable: Throwable): ParsedUniversalError {
        val msg = throwable.message ?: throwable.javaClass.simpleName
        if (throwable is SocketTimeoutException ||
            throwable is TimeoutException ||
            throwable is ConnectException ||
            throwable is SocketException ||
            msg.contains("Connection refused", ignoreCase = true) ||
            msg.contains("connect timed out", ignoreCase = true) ||
            msg.contains("Read timed out", ignoreCase = true) ||
            msg.contains("broken pipe", ignoreCase = true) ||
            msg.contains("connection reset", ignoreCase = true)
        ) {
            return ParsedUniversalError(
                category = UniversalErrorCategory.NETWORK_TIMEOUT_DROP,
                message = msg,
                raw = msg
            )
        }
        return parse(msg)
    }

    /** Parse HTTP error string extracting status code, JSON error bodies, and taxonomy category. */
    fun parse(raw: String, httpCode: Int? = null): ParsedUniversalError {
        val clean = raw.trim()
        val code = httpCode ?: extractHttpCode(clean)
        val jsonPayload = extractJsonPayload(clean)

        var errorMsg: String? = null
        var errorType: String? = null

        if (jsonPayload != null) {
            try {
                val json = JSONObject(jsonPayload)
                if (json.has("error")) {
                    val errVal = json.get("error")
                    if (errVal is JSONObject) {
                        errorMsg = errVal.optString("message", "").takeIf(String::isNotBlank)
                        errorType = errVal.optString("type", "").takeIf(String::isNotBlank)
                            ?: errVal.optString("code", "").takeIf(String::isNotBlank)
                    } else if (errVal is String) {
                        errorMsg = errVal
                    }
                }
                if (errorMsg == null && json.has("message")) {
                    errorMsg = json.optString("message", "").takeIf(String::isNotBlank)
                }
                if (errorType == null && json.has("type")) {
                    errorType = json.optString("type", "").takeIf(String::isNotBlank)
                }
            } catch (_: Exception) {
                // Soft-fail: fallback to substring heuristics on malformed JSON
            }
        }

        val effectiveMsg = errorMsg ?: clean
        val lower = "$effectiveMsg ${errorType ?: ""}".lowercase()

        val category = when {
            code == 413 || (code in listOf(400, 422) && isContextExceeded(lower)) || isContextExceeded(lower) ->
                UniversalErrorCategory.CONTEXT_EXCEEDED

            code == 429 || code == 503 || isRateLimitOrOverload(lower) ->
                UniversalErrorCategory.RATE_LIMIT_OVERLOAD

            code == 401 || code == 403 || isAuthOrDeadKey(lower) ->
                UniversalErrorCategory.AUTH_DEAD_KEY

            code in listOf(408, 504) || isNetworkTimeoutOrDrop(lower) ->
                UniversalErrorCategory.NETWORK_TIMEOUT_DROP

            else -> UniversalErrorCategory.UNKNOWN
        }

        return ParsedUniversalError(
            category = category,
            message = effectiveMsg,
            errorType = errorType,
            httpCode = code,
            raw = clean
        )
    }

    private fun isContextExceeded(lower: String): Boolean =
        lower.contains("context_length_exceeded") ||
            lower.contains("context length") ||
            lower.contains("maximum context") ||
            lower.contains("context window") ||
            lower.contains("token limit") ||
            lower.contains("tokens exceeded") ||
            lower.contains("prompt is too long") ||
            lower.contains("max_tokens") ||
            lower.contains("too large for model") ||
            lower.contains("exceeds model capacity")

    private fun isRateLimitOrOverload(lower: String): Boolean =
        lower.contains("rate limit") || lower.contains("too many requests") ||
            lower.contains("tokens per minute") || lower.contains("server overload") ||
            lower.contains("overloaded") || lower.contains("service unavailable")

    private fun isAuthOrDeadKey(lower: String): Boolean =
        lower.contains("invalid api key") || lower.contains("unauthorized") ||
            lower.contains("invalid_api_key") || lower.contains("forbidden") ||
            lower.contains("dead key") || lower.contains("auth failed")

    private fun isNetworkTimeoutOrDrop(lower: String): Boolean =
        lower.contains("timed out") || lower.contains("timeout") ||
            lower.contains("connection refused") || lower.contains("connection reset") ||
            lower.contains("broken pipe")

    private fun extractHttpCode(text: String): Int? {
        val match = Regex("""(?:HTTP|code|status)\s*[:=]?\s*(\d{3})""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractJsonPayload(text: String): String? {
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        return if (firstBrace >= 0 && lastBrace > firstBrace) {
            text.substring(firstBrace, lastBrace + 1)
        } else null
    }
}
