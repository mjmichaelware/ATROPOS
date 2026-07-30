package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult

object AssetProviderFixtures {
    private val successJson = """{"id":"asset-fixture","model":"asset-model","url":"local://asset-fixture.svg"}"""
    private val malformedJson = """{"result":{}}"""

    fun runAll(providerId: String): List<AdapterFixtureResult> {
        val success = AssetProviderJson.parseAssetResult(providerId, successJson)
        val malformed = AssetProviderJson.parseAssetResult(providerId, malformedJson)
        val empty = AssetProviderJson.parseAssetResult(providerId, "")

        return listOf(
            AdapterFixtureResult(providerId, "success", success is ProviderCallResult.Success, success.toString()),
            AdapterFixtureResult(providerId, "malformed", malformed is ProviderCallResult.Failure && malformed.failure.type == NormalizedProviderFailureType.MALFORMED_RESPONSE, malformed.toString()),
            AdapterFixtureResult(providerId, "empty", empty is ProviderCallResult.Failure && empty.failure.type == NormalizedProviderFailureType.EMPTY_RESPONSE, empty.toString())
        ) + ProviderFailureFixtures.normalized(providerId)
    }

    fun runAssetFamily(): List<AdapterFixtureResult> =
        AssetProviderCatalog.all().flatMap { runAll(it.providerId) }
}
