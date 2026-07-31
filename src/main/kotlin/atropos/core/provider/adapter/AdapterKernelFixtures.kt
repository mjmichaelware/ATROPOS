package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure

object AdapterKernelFixtures {
    private val successJson = """
        {
          "id": "fixture-request",
          "model": "fixture-model",
          "choices": [
            {
              "message": {
                "role": "assistant",
                "content": "fixture response"
              }
            }
          ],
          "usage": {
            "prompt_tokens": 5,
            "completion_tokens": 7
          }
        }
    """.trimIndent()

    private val authJson = """{"error":{"message":"invalid api key","type":"invalid_request_error"}}"""
    private val rateJson = """{"error":{"message":"rate limit exceeded","type":"rate_limit_error"}}"""
    private val billingJson = """{"error":{"message":"insufficient_quota","type":"billing_error"}}"""
    private val modelMissingJson = """{"error":{"message":"model does not exist","type":"invalid_request_error"}}"""
    private val malformedJson = """{"choices":[{"message":{}}]}"""

    fun runAll(providerId: String = "groq"): List<AdapterFixtureResult> {
        val success = AdapterJson.parseOpenAiCompatibleSuccess(providerId, successJson)
        val auth = AdapterJson.parseOpenAiCompatibleError(providerId, authJson)
        val rate = AdapterJson.parseOpenAiCompatibleError(providerId, rateJson)
        val billing = AdapterJson.parseOpenAiCompatibleError(providerId, billingJson)
        val missing = AdapterJson.parseOpenAiCompatibleError(providerId, modelMissingJson)
        val malformed = AdapterJson.parseOpenAiCompatibleSuccess(providerId, malformedJson)
        val empty = AdapterJson.parseOpenAiCompatibleSuccess(providerId, "")
        val timeout = ProviderErrorNormalizer().normalize(providerId, "timeout while calling provider")
        val unavailable = ProviderErrorNormalizer().normalize(providerId, "connection refused")
        val cancelled = ProviderFailure(providerId, NormalizedProviderFailureType.CANCELLED, "$providerId cancelled")

        return listOf(
            AdapterFixtureResult(providerId, "success", success is ProviderCallResult.Success && success.content == "fixture response", success.toString()),
            AdapterFixtureResult(providerId, "provider_error_auth", auth.type == NormalizedProviderFailureType.AUTH_FAILED, auth.toString()),
            AdapterFixtureResult(providerId, "provider_error_rate_limit", rate.type == NormalizedProviderFailureType.RATE_LIMITED, rate.toString()),
            AdapterFixtureResult(providerId, "provider_error_billing", billing.type == NormalizedProviderFailureType.BILLING_REQUIRED, billing.toString()),
            AdapterFixtureResult(providerId, "provider_error_model_missing", missing.type == NormalizedProviderFailureType.MODEL_MISSING, missing.toString()),
            AdapterFixtureResult(providerId, "malformed", malformed is ProviderCallResult.Failure && malformed.failure.type == NormalizedProviderFailureType.MALFORMED_RESPONSE, malformed.toString()),
            AdapterFixtureResult(providerId, "empty", empty is ProviderCallResult.Failure && empty.failure.type == NormalizedProviderFailureType.EMPTY_RESPONSE, empty.toString()),
            AdapterFixtureResult(providerId, "timeout", timeout.type == NormalizedProviderFailureType.TIMEOUT, timeout.toString()),
            AdapterFixtureResult(providerId, "unavailable", unavailable.type == NormalizedProviderFailureType.UNAVAILABLE, unavailable.toString()),
            AdapterFixtureResult(providerId, "cancelled", cancelled.type == NormalizedProviderFailureType.CANCELLED, cancelled.toString())
        )
    }

    fun runOpenAiCompatibleFamily(): List<AdapterFixtureResult> =
        OpenAiCompatibleProviderCatalog.all().flatMap { runAll(it.providerId) }
}
