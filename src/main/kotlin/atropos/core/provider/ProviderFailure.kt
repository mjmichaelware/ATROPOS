package atropos.core.provider

import atropos.core.security.RedactionFilter

enum class NormalizedProviderFailureType {
    AUTH_FAILED,
    RATE_LIMITED,
    QUOTA_EXHAUSTED,
    BILLING_REQUIRED,
    MODEL_MISSING,
    TIMEOUT,
    UNAVAILABLE,
    MALFORMED_RESPONSE,
    EMPTY_RESPONSE,
    CANCELLED,
    INTERNAL
}

data class ProviderFailure(
    val providerId: String,
    val type: NormalizedProviderFailureType,
    var cleanSummary: String,
    val retryAfterMs: Long? = null,
    val resetAtEpochMs: Long? = null,
    val terminal: Boolean = false
) {
    init {
        cleanSummary = ProviderRedactor.redact(cleanSummary)
    }
}

object ProviderRedactor {
    private val redactionFilter = RedactionFilter()
    private val absoluteUrl = Regex("""(?i)\bhttps?://[^\s\"'<>]+""")
    private val localServiceUrl = Regex("""http://127\.0\.0\.1:\d+[^\s\"'<>]*""")

    fun redact(value: String): String = redact(value, 320)

    fun redact(value: String, maxChars: Int): String =
        redactWithoutTruncation(value).let { if (maxChars < it.length) it.take(maxChars) else it }

    fun redactWithoutTruncation(value: String): String =
        redactionFilter.redact(value)
            .replace(localServiceUrl, "local service")
            .replace(absoluteUrl, "<redacted:url>")
}

class ProviderErrorNormalizer {
    fun normalize(providerId: String, failure: Throwable): ProviderFailure =
        normalize(providerId, failure.message ?: failure.javaClass.simpleName)

    fun normalize(providerId: String, raw: String): ProviderFailure {
        val clean = ProviderRedactor.redact(raw)
        val lower = clean.lowercase()
        return when {
            clean.isBlank() ->
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            "invalid api key" in lower || "unauthorized" in lower || "401" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.AUTH_FAILED, "$providerId auth failed", terminal = true)
            "billing" in lower || "payment" in lower || "insufficient_quota" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.BILLING_REQUIRED, "$providerId billing required", terminal = true)
            "rate limit" in lower || "429" in lower || "too many requests" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.RATE_LIMITED, "$providerId rate limited", retryAfterMs = 300_000)
            "quota" in lower || "exhausted" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.QUOTA_EXHAUSTED, "$providerId quota exhausted", retryAfterMs = 3_600_000)
            "model" in lower && ("missing" in lower || "not found" in lower || "does not exist" in lower) ->
                ProviderFailure(providerId, NormalizedProviderFailureType.MODEL_MISSING, "$providerId model missing", terminal = true)
            "timeout" in lower || "timed out" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.TIMEOUT, "$providerId timed out", retryAfterMs = 60_000)
            "cancel" in lower || "cancelled" in lower || "canceled" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.CANCELLED, "$providerId cancelled")
            "connection refused" in lower || "unavailable" in lower || "offline" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.UNAVAILABLE, "$providerId unavailable", retryAfterMs = 120_000)
            "malformed" in lower || "invalid json" in lower ->
                ProviderFailure(providerId, NormalizedProviderFailureType.MALFORMED_RESPONSE, "$providerId malformed response")
            else ->
                ProviderFailure(providerId, NormalizedProviderFailureType.INTERNAL, clean)
        }
    }
}
