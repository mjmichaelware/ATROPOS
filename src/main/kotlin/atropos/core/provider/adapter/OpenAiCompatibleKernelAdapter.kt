package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

internal class OpenAiCompatibleKernelAdapter(
    descriptor: ProviderDescriptor,
    private val spec: OpenAiCompatibleProviderSpec,
    env: Map<String, String> = System.getenv()
) : BaseKernelAdapter(
    descriptor = descriptor,
    env = env,
    transportImplemented = true,
    defaultModel = spec.defaultModel,
    modelIds = spec.models
), ChatProviderAdapter, CodeProviderAdapter, EmbeddingProviderAdapter, AssetProviderAdapter {
    override fun implemented(): Boolean = true

    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in descriptor.capabilities &&
            request.task.capability in setOf(
                ApiCapability.CHAT,
                ApiCapability.CODE,
                ApiCapability.REPAIR,
                ApiCapability.PLAN,
                ApiCapability.EMBED,
                ApiCapability.ASSET
            )

    override fun status(): AdapterStatus {
        val key = env[spec.apiKeyEnv]?.takeIf { it.isNotBlank() }
        return AdapterStatus(
            providerId = descriptor.id,
            implemented = true,
            configured = key != null,
            dryRunOnly = false,
            modelCount = spec.models.size,
            health = if (key != null) "live_ready" else "needs_${spec.apiKeyEnv.lowercase(Locale.US)}",
            detail = "${spec.displayName} openai-compatible adapter; live opt-in; model=${spec.defaultModel}"
        )
    }

    override fun dryRunContent(request: AdapterRequest): String =
        "provider=${descriptor.id} api=openai-compatible mode=dry_run model=${spec.defaultModel} prompt_chars=${request.prompt.length} deadline=${request.deadlineEpochMs}"

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val key = env[spec.apiKeyEnv]?.takeIf { it.isNotBlank() }
            ?: return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = descriptor.id,
                    type = NormalizedProviderFailureType.AUTH_FAILED,
                    cleanSummary = "${descriptor.id} missing ${spec.apiKeyEnv}",
                    terminal = true
                )
            )

        return try {
            val connection = (URI(spec.baseUrl).toURL().openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.connectTimeout = remainingMs(request).coerceIn(1_000, 30_000).toInt()
            connection.readTimeout = remainingMs(request).coerceIn(1_000, 60_000).toInt()
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            spec.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val body = AdapterJson.buildChatRequest(spec.defaultModel, request.prompt, request.context)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (code in 200..299) {
                AdapterJson.parseOpenAiCompatibleSuccess(descriptor.id, raw)
            } else {
                ProviderCallResult.Failure(AdapterJson.parseOpenAiCompatibleError(descriptor.id, raw.ifBlank { "HTTP $code" }))
            }
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(
                ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "${descriptor.id} timed out", retryAfterMs = 60_000)
            )
        } catch (failure: Exception) {
            ProviderCallResult.Failure(normalizer.normalize(descriptor.id, failure.message ?: failure.javaClass.simpleName))
        }
    }

    private fun remainingMs(request: AdapterRequest): Long =
        request.deadlineEpochMs - System.currentTimeMillis()
}
