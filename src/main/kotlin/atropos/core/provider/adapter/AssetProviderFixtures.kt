package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure

object AssetProviderFixtures {
    private val successJson = """{"id":"asset-fixture","model":"asset-model","url":"local://asset-fixture.svg"}"""
    private val authJson = """{"error":{"message":"unauthorized token"}}"""
    private val malformedJson = """{"result":{}}"""

    fun runAll(providerId: String): List<AdapterFixtureResult> {
        val success = AssetProviderJson.parseAssetResult(providerId, successJson)
        val auth = ProviderErrorNormalizer().normalize(providerId, authJson)
        val malformed = AssetProviderJson.parseAssetResult(providerId, malformedJson)
        val empty = AssetProviderJson.parseAssetResult(providerId, "")
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

    fun runAssetFamily(): List<AdapterFixtureResult> =
        AssetProviderCatalog.all().flatMap { runAll(it.providerId) }
}
