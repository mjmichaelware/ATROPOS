package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure

object ProviderFailureFixtures {
    private val normalizer = ProviderErrorNormalizer()

    fun normalized(providerId: String): List<AdapterFixtureResult> {
        val auth = normalizer.normalize(providerId, """{"error":{"message":"401 unauthorized invalid api key"}}""")
        val rate = normalizer.normalize(providerId, """{"error":{"message":"429 rate limit exceeded"}}""")
        val billing = normalizer.normalize(providerId, """{"error":{"message":"billing required insufficient_quota"}}""")
        val timeout = normalizer.normalize(providerId, "request timed out while calling provider")
        val unavailable = normalizer.normalize(providerId, "connection refused")
        val cancelled = ProviderFailure(providerId, NormalizedProviderFailureType.CANCELLED, "$providerId cancelled")

        return listOf(
            AdapterFixtureResult(providerId, "provider_error_auth", auth.type == NormalizedProviderFailureType.AUTH_FAILED, auth.toString()),
            AdapterFixtureResult(providerId, "provider_error_rate_limit", rate.type == NormalizedProviderFailureType.RATE_LIMITED, rate.toString()),
            AdapterFixtureResult(providerId, "provider_error_billing", billing.type == NormalizedProviderFailureType.BILLING_REQUIRED, billing.toString()),
            AdapterFixtureResult(providerId, "unavailable", unavailable.type == NormalizedProviderFailureType.UNAVAILABLE, unavailable.toString()),
            AdapterFixtureResult(providerId, "timeout", timeout.type == NormalizedProviderFailureType.TIMEOUT, timeout.toString()),
            AdapterFixtureResult(providerId, "cancelled", cancelled.type == NormalizedProviderFailureType.CANCELLED, cancelled.toString())
        )
    }
}
