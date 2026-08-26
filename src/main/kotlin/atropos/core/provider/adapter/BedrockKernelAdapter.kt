/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage
import java.net.URI
import java.util.Locale

fun interface BedrockTransport {
    fun send(request: BedrockRequest): BedrockResponse
}

internal class BedrockKernelAdapter(
    descriptor: ProviderDescriptor,
    env: Map<String, String> = System.getenv(),
    private val transport: BedrockTransport = BedrockTransport(::sendOverHttps)
) : BaseKernelAdapter(
    descriptor = descriptor,
    env = env,
    transportImplemented = true,
    defaultModel = env["AWS_BEDROCK_MODEL"].orEmpty().ifBlank { DEFAULT_MODEL }
), ChatProviderAdapter, CodeProviderAdapter {
    override fun implemented(): Boolean = true

    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in descriptor.capabilities &&
            request.task.capability in setOf(atropos.core.provider.ApiCapability.CHAT, atropos.core.provider.ApiCapability.CODE, atropos.core.provider.ApiCapability.REPAIR, atropos.core.provider.ApiCapability.PLAN)

    override fun status(): AdapterStatus {
        val configured = descriptor.requiredEnv.all { !env[it].isNullOrBlank() }
        return AdapterStatus(
            providerId = descriptor.id,
            implemented = true,
            configured = configured,
            dryRunOnly = false,
            modelCount = 1,
            health = if (configured) "live_ready" else "needs_aws_credentials",
            detail = "AWS Bedrock SigV4 transport; model=${env["AWS_BEDROCK_MODEL"].orEmpty().ifBlank { DEFAULT_MODEL }}"
        )
    }

    override fun dryRunContent(request: AdapterRequest): String =
        "provider=aws_bedrock api=converse mode=dry_run model=${env["AWS_BEDROCK_MODEL"].orEmpty().ifBlank { DEFAULT_MODEL }} prompt_chars=${request.prompt.length}"

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        val access = env["AWS_ACCESS_KEY_ID"]?.takeIf(String::isNotBlank)
        val secret = env["AWS_SECRET_ACCESS_KEY"]?.takeIf(String::isNotBlank)
        val region = env["AWS_REGION"]?.takeIf(String::isNotBlank) ?: env["AWS_DEFAULT_REGION"]?.takeIf(String::isNotBlank)
        if (access == null || secret == null || region == null) {
            return ProviderCallResult.Failure(
                ProviderFailure(descriptor.id, NormalizedProviderFailureType.AUTH_FAILED, "aws_bedrock requires AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, and AWS_REGION", terminal = true)
            )
        }
        val model = env["AWS_BEDROCK_MODEL"].orEmpty().ifBlank { DEFAULT_MODEL }
        val endpoint = "https://bedrock-runtime.$region.amazonaws.com/model/$model/converse"
        val body = buildRequest(request.prompt, request.context)
        return try {
            val response = transport.send(
                BedrockRequest(
                    endpoint = endpoint,
                    body = body,
                    credentials = AwsSigV4Credentials(access, secret, env["AWS_SESSION_TOKEN"]),
                    region = region,
                    timeoutMs = remainingMs(request).coerceIn(1_000, 60_000)
                )
            )
            if (response.status in 200..299) {
                ProviderCallResult.Success(
                    providerId = descriptor.id,
                    content = parseText(response.body),
                    usage = ProviderUsage(),
                    model = model,
                    requestId = "bedrock-${descriptor.id}"
                )
            } else {
                ProviderCallResult.Failure(AdapterJson.parseOpenAiCompatibleError(descriptor.id, response.body.ifBlank { "HTTP ${response.status}" }))
            }
        } catch (failure: java.net.SocketTimeoutException) {
            ProviderCallResult.Failure(ProviderFailure(descriptor.id, NormalizedProviderFailureType.TIMEOUT, "aws_bedrock timed out", retryAfterMs = 60_000))
        } catch (failure: Exception) {
            ProviderCallResult.Failure(normalizer.normalize(descriptor.id, failure))
        }
    }

    private fun buildRequest(prompt: String, context: String): String {
        val text = if (context.isBlank()) prompt else "$context\n\n$prompt"
        return "{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"${escape(text)}\"}]}]}"
    }

    private fun parseText(body: String): String = Regex("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
        .find(body)?.groupValues?.getOrNull(1)?.replace("\\\"", "\"")?.replace("\\n", "\n")
        ?: body.take(16_000)

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun remainingMs(request: AdapterRequest): Long = request.deadlineEpochMs - System.currentTimeMillis()

    private companion object {
        const val DEFAULT_MODEL = "anthropic.claude-3-haiku-20240307-v1:0"
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

        fun sendOverHttps(request: BedrockRequest): BedrockResponse {
            val connection = CredentialSafeHttpTransport.open(URI(request.endpoint))
            connection.requestMethod = "POST"
            connection.connectTimeout = request.timeoutMs.coerceIn(1_000, 30_000).toInt()
            connection.readTimeout = request.timeoutMs.coerceIn(1_000, 60_000).toInt()
            connection.doOutput = true
            val headers = AwsSigV4.sign("POST", URI(request.endpoint), request.body, request.credentials, request.region)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                input.readNBytes(MAX_RESPONSE_BYTES + 1).also {
                    require(it.size <= MAX_RESPONSE_BYTES) {
                        "${request.region} Bedrock response exceeded $MAX_RESPONSE_BYTES bytes"
                    }
                }.toString(Charsets.UTF_8)
            }.orEmpty()
            return BedrockResponse(status, body)
        }
    }
}

data class BedrockRequest(
    val endpoint: String,
    val body: String,
    val credentials: AwsSigV4Credentials,
    val region: String,
    val timeoutMs: Long
)

data class BedrockResponse(val status: Int, val body: String)
