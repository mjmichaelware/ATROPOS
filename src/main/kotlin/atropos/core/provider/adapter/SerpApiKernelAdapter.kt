package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import java.net.HttpURLConnection
import java.net.URI

internal class SerpApiKernelAdapter(
    descriptor: ProviderDescriptor,
    spec: DataInfraProviderSpec,
    env: Map<String, String> = System.getenv()
) : DataInfraKernelAdapter(descriptor, spec, env), SearchProviderAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability == ApiCapability.WEB

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val key = env["SERPAPI_API_KEY"]?.takeIf { it.isNotBlank() } ?: return missingSecret()
        return try {
            val query = java.net.URLEncoder.encode(request.prompt, "UTF-8")
            val endpoint = "${spec.endpoint}?engine=google&q=$query&api_key=$key"
            val connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection)
            connection.requestMethod = "GET"
            connection.connectTimeout = remainingMs(request)
            connection.readTimeout = remainingMs(request)
            val (code, raw) = readResponse(connection)
            if (code in 200..299) DataInfraJson.parseSearchResult(descriptor.id, raw)
            else ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, raw.ifBlank { "HTTP $code" }))
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "${descriptor.id} timed out", retryAfterMs = 60_000))
        } catch (failure: Exception) {
            ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, failure.message ?: failure.javaClass.simpleName))
        }
    }
}
