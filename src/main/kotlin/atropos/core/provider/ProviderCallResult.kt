package atropos.core.provider

data class ProviderUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val estimatedCostMicros: Long = 0,
    val latencyMs: Long = 0
)

sealed class ProviderCallResult {
    data class Success(val providerId: String, val content: String, val usage: ProviderUsage = ProviderUsage(), val model: String? = null, val requestId: String? = null) : ProviderCallResult()
    data class Failure(val failure: ProviderFailure) : ProviderCallResult()
    data class Queued(val task: ProviderTask, val earliestRetryEpochMs: Long, val reason: String) : ProviderCallResult()
    data class LocalOnly(val task: ProviderTask, val content: String) : ProviderCallResult()
}
