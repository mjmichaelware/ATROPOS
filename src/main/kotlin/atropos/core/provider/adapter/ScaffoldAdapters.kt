package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderTask
import atropos.core.provider.ProviderUsage
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AdapterModel(
    val id: String,
    val free: Boolean,
    val capabilities: Set<ApiCapability>
)

data class AdapterFixtureResult(
    val providerId: String,
    val fixture: String,
    val passed: Boolean,
    val detail: String
)

data class OpenAiCompatibleProviderSpec(
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val fallbackModels: List<String>,
    val apiKeyEnv: String,
    val freeTier: Boolean,
    val headers: Map<String, String> = emptyMap()
) {
    val models: List<String> = (listOf(defaultModel) + fallbackModels).distinct()
}

object OpenAiCompatibleProviderCatalog {
    private val specs = listOf(
        OpenAiCompatibleProviderSpec(
            providerId = "groq",
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/chat/completions",
            defaultModel = "llama-3.1-8b-instant",
            fallbackModels = listOf("llama-3.3-70b-versatile"),
            apiKeyEnv = "GROQ_API_KEY",
            freeTier = true
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "openrouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            defaultModel = "meta-llama/llama-3.1-8b-instruct:free",
            fallbackModels = listOf("mistralai/mistral-7b-instruct:free"),
            apiKeyEnv = "OPENROUTER_API_KEY",
            freeTier = true,
            headers = mapOf(
                "HTTP-Referer" to "https://local.atropos.invalid",
                "X-Title" to "ATROPOS"
            )
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "deepinfra",
            displayName = "DeepInfra",
            baseUrl = "https://api.deepinfra.com/v1/openai/chat/completions",
            defaultModel = "meta-llama/Meta-Llama-3.1-8B-Instruct",
            fallbackModels = listOf("mistralai/Mistral-7B-Instruct-v0.3"),
            apiKeyEnv = "DEEPINFRA_API_KEY",
            freeTier = false
        ),
        OpenAiCompatibleProviderSpec(
            providerId = "siliconflow",
            displayName = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1/chat/completions",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
            fallbackModels = listOf("THUDM/glm-4-9b-chat"),
            apiKeyEnv = "SILICONFLOW_API_KEY",
            freeTier = false
        )
    )

    fun get(providerId: String): OpenAiCompatibleProviderSpec? =
        specs.firstOrNull { it.providerId == providerId }

    fun all(): List<OpenAiCompatibleProviderSpec> = specs
}

object AdapterJson {
    fun buildChatRequest(model: String, prompt: String, context: String): String = buildString {
        append("{")
        append("\"model\":\"").append(escape(model)).append("\",")
        append("\"messages\":[")
        append("{\"role\":\"system\",\"content\":\"").append(escape(context.ifBlank { "ATROPOS local-first provider adapter" })).append("\"},")
        append("{\"role\":\"user\",\"content\":\"").append(escape(prompt)).append("\"}")
        append("],")
        append("\"temperature\":0.2")
        append("}")
    }

    fun parseOpenAiCompatibleSuccess(providerId: String, json: String): ProviderCallResult {
        if (json.isBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            )
        }

        if (json.contains("\"error\"")) {
            return ProviderCallResult.Failure(parseOpenAiCompatibleError(providerId, json))
        }

        val content = stringField(json, "content")
        if (content.isNullOrBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.MALFORMED_RESPONSE, "$providerId missing assistant content")
            )
        }

        return ProviderCallResult.Success(
            providerId = providerId,
            content = content,
            usage = ProviderUsage(
                inputTokens = intField(json, "prompt_tokens") ?: 0,
                outputTokens = intField(json, "completion_tokens") ?: 0
            ),
            model = stringField(json, "model"),
            requestId = stringField(json, "id")
        )
    }

    fun parseOpenAiCompatibleError(providerId: String, json: String): ProviderFailure {
        val message = stringField(json, "message") ?: json
        return ProviderErrorNormalizer().normalize(providerId, message)
    }

    private fun stringField(json: String, name: String): String? {
        val regex = Regex(""""$name"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return regex.find(json)?.groupValues?.get(1)?.let(::unescape)
    }

    private fun intField(json: String, name: String): Int? {
        val regex = Regex(""""$name"\s*:\s*([0-9]+)""")
        return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun escape(value: String): String = buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    else -> out.append(value[i + 1])
                }
                i += 2
            } else {
                out.append(ch)
                i += 1
            }
        }
        return out.toString()
    }
}

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
            AdapterFixtureResult(providerId, "cancelled", cancelled.type == NormalizedProviderFailureType.CANCELLED, cancelled.toString())
        )
    }

    fun runOpenAiCompatibleFamily(): List<AdapterFixtureResult> =
        OpenAiCompatibleProviderCatalog.all().flatMap { runAll(it.providerId) }
}

private abstract class BaseKernelAdapter(
    final override val descriptor: ProviderDescriptor,
    protected val env: Map<String, String> = System.getenv(),
    private val transportImplemented: Boolean = false,
    private val defaultModel: String = "${descriptor.id}-default",
    private val modelIds: List<String> = listOf(defaultModel)
) : ProviderAdapter {
    protected val normalizer = ProviderErrorNormalizer()

    override fun status(): AdapterStatus {
        val configured = descriptor.isLocal || descriptor.requiredEnv.all { env[it].isNullOrBlank().not() }
        val health = when {
            descriptor.isLocal -> "ready"
            !implemented() -> "contract_only"
            configured && transportImplemented -> "live_ready"
            configured -> "kernel_ready"
            transportImplemented -> "needs_key"
            else -> "missing_key"
        }

        return AdapterStatus(
            providerId = descriptor.id,
            implemented = implemented(),
            configured = configured,
            dryRunOnly = !transportImplemented,
            modelCount = modelIds.size,
            health = health,
            detail = when {
                descriptor.isLocal -> "local adapter"
                transportImplemented -> "openai-compatible transport implemented; live tests opt-in"
                implemented() -> "fixture-backed adapter kernel"
                else -> "descriptor registered; provider schema pending"
            }
        )
    }

    override fun complete(request: AdapterRequest): ProviderCallResult {
        if (!canHandle(request)) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = descriptor.id,
                    type = NormalizedProviderFailureType.MALFORMED_RESPONSE,
                    cleanSummary = "${descriptor.id} cannot handle ${request.task.capability.name.lowercase(Locale.US)}"
                )
            )
        }

        if (request.deadlineEpochMs <= System.currentTimeMillis()) {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = descriptor.id,
                    type = NormalizedProviderFailureType.TIMEOUT,
                    cleanSummary = "${descriptor.id} request deadline expired",
                    retryAfterMs = 60_000
                )
            )
        }

        if (request.metadata["cancelled"] == "true") {
            return ProviderCallResult.Failure(
                ProviderFailure(
                    providerId = descriptor.id,
                    type = NormalizedProviderFailureType.CANCELLED,
                    cleanSummary = "${descriptor.id} request cancelled"
                )
            )
        }

        if (request.dryRun) {
            return ProviderCallResult.Success(
                providerId = descriptor.id,
                content = dryRunContent(request),
                usage = ProviderUsage(),
                model = modelIds.firstOrNull(),
                requestId = "dry-run-${descriptor.id}"
            )
        }

        if (!request.liveNetworkAllowed || !transportImplemented) {
            return ProviderCallResult.Queued(
                task = request.task,
                earliestRetryEpochMs = System.currentTimeMillis() + 300_000L,
                reason = "${descriptor.id} live network requires ATROPOS_LIVE_PROVIDER_TESTS=1"
            )
        }

        return liveComplete(request)
    }

    protected open fun implemented(): Boolean = false

    protected open fun liveComplete(request: AdapterRequest): ProviderCallResult =
        ProviderCallResult.Failure(normalizer.normalize(descriptor.id, "${descriptor.id} live transport unavailable"))

    protected open fun dryRunContent(request: AdapterRequest): String =
        "adapter=${descriptor.id} kernel_dry_run task=${request.task.kind.name.lowercase(Locale.US)} model=${modelIds.firstOrNull() ?: "none"} deadline=${request.deadlineEpochMs}"
}

private class LocalKernelAdapter(
    descriptor: ProviderDescriptor
) : BaseKernelAdapter(
    descriptor = descriptor,
    transportImplemented = true,
    defaultModel = "local-toolchain",
    modelIds = listOf("local-toolchain")
), ChatProviderAdapter, CodeProviderAdapter, StorageProviderAdapter, EdgeExecutionAdapter {
    override fun implemented(): Boolean = true

    override fun complete(request: AdapterRequest): ProviderCallResult =
        ProviderCallResult.LocalOnly(
            task = request.task,
            content = "local adapter ready task=${request.task.kind.name.lowercase(Locale.US)}"
        )
}

private class OpenAiCompatibleKernelAdapter(
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
            val connection = (URL(spec.baseUrl).openConnection() as HttpURLConnection)
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

private class DescriptorOnlyKernelAdapter(
    descriptor: ProviderDescriptor
) : BaseKernelAdapter(descriptor = descriptor),
    ChatProviderAdapter,
    CodeProviderAdapter,
    EmbeddingProviderAdapter,
    SearchProviderAdapter,
    StorageProviderAdapter,
    EdgeExecutionAdapter,
    AssetProviderAdapter {
    override fun implemented(): Boolean = false
}

fun buildKernelAdapter(descriptor: ProviderDescriptor): ProviderAdapter =
    when {
        descriptor.isLocal || descriptor.id == "local" || descriptor.id == "ollama" ->
            LocalKernelAdapter(descriptor)
        OpenAiCompatibleProviderCatalog.get(descriptor.id) != null ->
            OpenAiCompatibleKernelAdapter(
                descriptor = descriptor,
                spec = OpenAiCompatibleProviderCatalog.get(descriptor.id)!!
            )
        else ->
            DescriptorOnlyKernelAdapter(descriptor)
    }
