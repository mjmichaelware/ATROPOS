package atropos.datacenter.harvester

/**
 * Quota state parsed from platform HTTP response headers
 * (X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After).
 */
data class ApiQuotaState(
    val platform: String,
    val remaining: Int,
    val resetAtEpochSeconds: Long,
    val isThrottled: Boolean
)
