/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.net.ConnectException
import java.util.concurrent.TimeoutException

enum class FailureType {
    MISSING_KEY,
    AUTH_INVALID,
    RATE_LIMIT,
    CONNECTION_REFUSED,
    TIMEOUT,
    MODEL_MISSING,
    BAD_JSON,
    UNKNOWN
}

data class ProviderError(
    val provider: String,
    val type: FailureType,
    val cleanMessage: String,
    val rawMessage: String = ""
)

data class OllamaStatus(
    val online: Boolean,
    val models: List<String>,
    val selectedModel: String,
    val host: String
)

class ProviderFailureClassifier {
    fun classify(providerName: String, failure: Exception): ProviderError {
        val provider = providerName.lowercase()
        val message = failure.message ?: failure.javaClass.simpleName
        val lower = message.lowercase()
        val debug = System.getenv("ATROPOS_DEBUG_ERRORS") == "true"
        val raw = if (debug) message else ""

        return when {
            lower.contains("credential bindings are empty") ||
                lower.contains("api key is missing") ||
                lower.contains("missing api key") ->
                ProviderError(
                    provider,
                    FailureType.MISSING_KEY,
                    "$provider auth failed: missing API key",
                    raw
                )

            lower.contains("http 401") ||
                lower.contains("invalid api key") ||
                lower.contains("invalid_api_key") ->
                ProviderError(
                    provider,
                    FailureType.AUTH_INVALID,
                    "$provider auth failed: invalid API key",
                    raw
                )

            lower.contains("http 429") ||
                lower.contains("rate limit") ->
                ProviderError(
                    provider,
                    FailureType.RATE_LIMIT,
                    "$provider rate limit exceeded",
                    raw
                )

            failure is ConnectException ||
                lower.contains("connection refused") ||
                lower.contains("connectexception") ->
                ProviderError(
                    provider,
                    FailureType.CONNECTION_REFUSED,
                    "$provider unavailable",
                    raw
                )

            failure is TimeoutException ||
                lower.contains("timeout") ||
                lower.contains("timeoutexception") ->
                ProviderError(
                    provider,
                    FailureType.TIMEOUT,
                    "$provider timed out",
                    raw
                )

            lower.contains("http 404") ||
                lower.contains("model not found") ||
                lower.contains("model missing") ||
                lower.contains("model_missing") ->
                ProviderError(
                    provider,
                    FailureType.MODEL_MISSING,
                    "$provider model missing",
                    raw
                )

            lower.contains("bad json") ||
                lower.contains("malformed json") ||
                lower.contains("jsonparseexception") ->
                ProviderError(
                    provider,
                    FailureType.BAD_JSON,
                    "$provider response parse failed",
                    raw
                )

            else ->
                ProviderError(
                    provider,
                    FailureType.UNKNOWN,
                    "$provider call failed: $message",
                    raw
                )
        }
    }
}
