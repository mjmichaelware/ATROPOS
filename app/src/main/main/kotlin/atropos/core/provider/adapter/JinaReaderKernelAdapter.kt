package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage
import java.net.HttpURLConnection
import java.net.URI

internal class JinaReaderKernelAdapter(
    descriptor: ProviderDescriptor,
    spec: DataInfraProviderSpec,
    env: Map<String, String> = System.getenv()
) : DataInfraKernelAdapter(descriptor, spec, env), SearchProviderAdapter, EmbeddingProviderAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in setOf(ApiCapability.READER, ApiCapability.WEB, ApiCapability.EMBED)

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val key = env["JINA_API_KEY"]?.takeIf { it.isNotBlank() } ?: return missingSecret()
        return try {
            val target = request.metadata["url"] ?: request.prompt.trim().ifBlank { "https://example.com" }
            val endpoint = "https://r.jina.ai/http://" + target.removePrefix("http://").removePrefix("https://")
            val connection = CredentialSafeHttpTransport.open(URI(endpoint))
            connection.requestMethod = "GET"
            connection.connectTimeout = remainingMs(request)
            connection.readTimeout = remainingMs(request)
            connection.setRequestProperty("Authorization", "Bearer $key")
            val (code, raw) = readResponse(connection)
            if (code in 200..299) {
                ProviderCallResult.Success(descriptor.id, raw.take(4000), ProviderUsage(), model = "jina-reader", requestId = "jina-reader")
            } else {
                ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, raw.ifBlank { "HTTP $code" }))
            }
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "${descriptor.id} timed out", retryAfterMs = 60_000))
        } catch (failure: Exception) {
            ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, failure))
        }
    }
}
