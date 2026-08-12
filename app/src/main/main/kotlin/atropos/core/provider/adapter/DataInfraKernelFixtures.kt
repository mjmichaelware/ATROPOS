package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import java.util.Locale

object DataInfraKernelFixtures {
    private val searchSuccess = """{"organic_results":[{"title":"fixture research result","snippet":"local-first provider docs"}]}"""
    private val textSuccess = """{"title":"fixture research result","content":"local-first provider docs"}"""
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
        val malformed = DataInfraJson.parseSearchResult(providerId, malformedJson)
        val empty = DataInfraJson.parseSearchResult(providerId, "")

        return listOf(
            AdapterFixtureResult(providerId, "success", success is ProviderCallResult.Success, success.toString()),
            AdapterFixtureResult(providerId, "malformed", malformed is ProviderCallResult.Failure && malformed.failure.type == NormalizedProviderFailureType.MALFORMED_RESPONSE, malformed.toString()),
            AdapterFixtureResult(providerId, "empty", empty is ProviderCallResult.Failure && empty.failure.type == NormalizedProviderFailureType.EMPTY_RESPONSE, empty.toString())
        ) + ProviderFailureFixtures.normalized(providerId)
    }

    fun runDataInfraResearchFamily(): List<AdapterFixtureResult> =
        DataInfraResearchProviderCatalog.all().flatMap { runAll(it.providerId) }
}
