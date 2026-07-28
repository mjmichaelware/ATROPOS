package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import java.util.Locale

object DataInfraKernelFixtures {
    private val searchSuccess = """{"organic_results":[{"title":"fixture research result","snippet":"local-first provider docs"}]}"""
    private val textSuccess = """{"title":"fixture research result","content":"local-first provider docs"}"""
    private val authJson = """{"error":{"message":"unauthorized token"}}"""
    private val malformedJson = """{"result":{}}"""

    fun runAll(providerId: String): List<AdapterFixtureResult> {
        val spec = DataInfraResearchProviderCatalog.get(providerId)
            ?: return listOf(AdapterFixtureResult(providerId, "missing_spec", false, "missing spec"))

        val success = when (spec.schema) {
            DataInfraProviderSchema.JINA_READER,
            DataInfraProviderSchema.SERPAPI_WEB ->
                DataInfraJson.parseSearchResult(providerId, textSuccess)
            else ->
                DataInfraJson.planResult(providerId, "${spec.displayName} local fallback: ${spec.localFallback}", spec.schema.name.lowercase(Locale.US))
        }
        val auth = ProviderErrorNormalizer().normalize(providerId, authJson)
        val malformed = DataInfraJson.parseSearchResult(providerId, malformedJson)
        val empty = DataInfraJson.parseSearchResult(providerId, "")
        val timeout = ProviderErrorNormalizer().normalize(providerId, "timeout while calling provider")
        val cancelled = ProviderFailure(providerId, NormalizedProviderFailureType.CANCELLED, "$providerId cancelled")

        return listOf(
            AdapterFixtureResult(providerId, "success", success is ProviderCallResult.Success, success.toString()),
            AdapterFixtureResult(providerId, "provider_error_auth", auth.type == NormalizedProviderFailureType.AUTH_FAILED, auth.toString()),
            AdapterFixtureResult(providerId, "malformed", malformed is ProviderCallResult.Failure && malformed.failure.type == NormalizedProviderFailureType.MALFORMED_RESPONSE, malformed.toString()),
            AdapterFixtureResult(providerId, "empty", empty is ProviderCallResult.Failure && empty.failure.type == NormalizedProviderFailureType.EMPTY_RESPONSE, empty.toString()),
            AdapterFixtureResult(providerId, "timeout", timeout.type == NormalizedProviderFailureType.TIMEOUT, timeout.toString()),
            AdapterFixtureResult(providerId, "cancelled", cancelled.type == NormalizedProviderFailureType.CANCELLED, cancelled.toString())
        )
    }

    fun runDataInfraResearchFamily(): List<AdapterFixtureResult> =
        DataInfraResearchProviderCatalog.all().flatMap { runAll(it.providerId) }
}
