package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

internal class CloudflareAiKernelAdapter(
    descriptor: ProviderDescriptor,
    spec: NonOpenAiProviderSpec,
    env: Map<String, String> = System.getenv()
) : NonOpenAiFreeKernelAdapter(descriptor, spec, env), ChatProviderAdapter, EmbeddingProviderAdapter, EdgeExecutionAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in setOf(ApiCapability.CHAT, ApiCapability.EMBED, ApiCapability.EDGE)

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val token = env["CLOUDFLARE_API_TOKEN"]?.takeIf { it.isNotBlank() } ?: return missingSecret()
        val account = env["CLOUDFLARE_ACCOUNT_ID"]?.takeIf { it.isNotBlank() } ?: return missingSecret()
        return try {
            val endpoint = spec.endpoint
                .replace("{account}", account)
                .replace("{model}", spec.defaultModel)
            val connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.connectTimeout = remainingMs(request)
            connection.readTimeout = remainingMs(request)
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            val body = NonOpenAiJson.buildCloudflareAiRequest(request.prompt, request.context)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val (code, raw) = readResponse(connection)
            if (code in 200..299) NonOpenAiJson.parseTextResult(descriptor.id, raw)
            else ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, raw.ifBlank { "HTTP $code" }))
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "${descriptor.id} timed out", retryAfterMs = 60_000))
        } catch (failure: Exception) {
            ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, failure.message ?: failure.javaClass.simpleName))
        }
    }
}
