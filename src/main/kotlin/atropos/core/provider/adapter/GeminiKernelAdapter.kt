package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import java.net.HttpURLConnection
import java.net.URI

internal class GeminiKernelAdapter(
    descriptor: ProviderDescriptor,
    spec: NonOpenAiProviderSpec,
    env: Map<String, String> = System.getenv()
) : NonOpenAiFreeKernelAdapter(descriptor, spec, env), ChatProviderAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in setOf(ApiCapability.CHAT, ApiCapability.PLAN, ApiCapability.LARGE_CONTEXT, ApiCapability.VISION)

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val key = env["GEMINI_API_KEY"]?.takeIf { it.isNotBlank() } ?: return missingSecret()
        return try {
            val url = spec.endpoint.replace("{model}", spec.defaultModel) + "?key=$key"
            val connection = CredentialSafeHttpTransport.open(URI(url))
            connection.requestMethod = "POST"
            connection.connectTimeout = remainingMs(request)
            connection.readTimeout = remainingMs(request)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = NonOpenAiJson.buildGeminiRequest(request.prompt, request.context)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val (code, raw) = readResponse(connection)
            if (code in 200..299) NonOpenAiJson.parseTextResult(descriptor.id, raw)
            else ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, raw.ifBlank { "HTTP $code" }))
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "${descriptor.id} timed out", retryAfterMs = 60_000))
        } catch (failure: Exception) {
            ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, failure))
        }
    }
}
