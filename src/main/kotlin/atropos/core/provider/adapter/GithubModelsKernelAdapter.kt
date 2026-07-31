package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import java.net.HttpURLConnection
import java.net.URI

internal class GithubModelsKernelAdapter(
    descriptor: ProviderDescriptor,
    spec: NonOpenAiProviderSpec,
    env: Map<String, String> = System.getenv()
) : NonOpenAiFreeKernelAdapter(descriptor, spec, env), ChatProviderAdapter, CodeProviderAdapter, EdgeExecutionAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in setOf(ApiCapability.CHAT, ApiCapability.CODE, ApiCapability.PLAN, ApiCapability.CI)

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val key = env["GITHUB_MODELS_TOKEN"]?.takeIf { it.isNotBlank() } ?: return missingSecret()
        return try {
            val connection = CredentialSafeHttpTransport.open(URI(spec.endpoint))
            connection.requestMethod = "POST"
            connection.connectTimeout = remainingMs(request)
            connection.readTimeout = remainingMs(request)
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            val body = AdapterJson.buildChatRequest(spec.defaultModel, request.prompt, request.context)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val (code, raw) = readResponse(connection)
            if (code in 200..299) AdapterJson.parseOpenAiCompatibleSuccess(descriptor.id, raw)
            else ProviderCallResult.Failure(AdapterJson.parseOpenAiCompatibleError(descriptor.id, raw.ifBlank { "HTTP $code" }))
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "${descriptor.id} timed out", retryAfterMs = 60_000))
        } catch (failure: Exception) {
            ProviderCallResult.Failure(ProviderErrorNormalizer().normalize(descriptor.id, failure))
        }
    }
}
