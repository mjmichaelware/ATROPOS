package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure

object NonOpenAiKernelFixtures {
    private val geminiSuccess = """{"candidates":[{"content":{"parts":[{"text":"gemini fixture response"}]}}]}"""
    private val cloudflareSuccess = """{"success":true,"result":{"response":"cloudflare fixture response"}}"""
    private val githubSuccess = """{"id":"fixture","model":"github-fixture","choices":[{"message":{"content":"github fixture response"}}]}"""
    private val authJson = """{"error":{"message":"unauthorized api key"}}"""
    private val malformedJson = """{"result":{}}"""

    fun runAll(providerId: String): List<AdapterFixtureResult> {
        val spec = NonOpenAiFreeProviderCatalog.get(providerId)
            ?: return listOf(AdapterFixtureResult(providerId, "missing_spec", false, "missing spec"))

        val success = when (spec.schema) {
            NonOpenAiProviderSchema.GEMINI -> NonOpenAiJson.parseTextResult(providerId, geminiSuccess)
            NonOpenAiProviderSchema.GITHUB_MODELS -> AdapterJson.parseOpenAiCompatibleSuccess(providerId, githubSuccess)
            NonOpenAiProviderSchema.CLOUDFLARE_AI,
            NonOpenAiProviderSchema.CLOUDFLARE_WORKERS -> NonOpenAiJson.parseTextResult(providerId, cloudflareSuccess)
        }
        val auth = ProviderErrorNormalizer().normalize(providerId, authJson)
        val malformed = NonOpenAiJson.parseTextResult(providerId, malformedJson)
        val empty = NonOpenAiJson.parseTextResult(providerId, "")
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

    fun runNonOpenAiFreeFamily(): List<AdapterFixtureResult> =
        NonOpenAiFreeProviderCatalog.all().flatMap { runAll(it.providerId) }
}
