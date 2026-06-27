package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderTask
import atropos.core.provider.ProviderUsage
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

object AdapterJson {
    fun buildChatRequest(
        model: String,
        prompt: String,
        context: String
    ): String = buildString {
        append("{")
        append("\"model\":\"").append(escape(model)).append("\",")
        append("\"messages\":[")
        append("{\"role\":\"system\",\"content\":\"").append(escape(context.ifBlank { "ATROPOS local-first provider adapter" })).append("\"},")
        append("{\"role\":\"user\",\"content\":\"").append(escape(prompt)).append("\"}")
        append("]}")
    }

    fun parseOpenAiCompatibleSuccess(providerId: String, json: String): ProviderCallResult {
        if (json.isBlank()) {
            return ProviderCallResult.Failure(
                ProviderFailure(providerId, NormalizedProviderFailureType.EMPTY_RESPONSE, "$providerId empty response")
            )
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
    private val malformedJson = """{"choices":[{"message":{}}]}"""

    fun runAll(providerId: String = "groq"): List<AdapterFixtureResult> {
        val success = AdapterJson.parseOpenAiCompatibleSuccess(providerId, successJson)
        val auth = AdapterJson.parseOpenAiCompatibleError(providerId, authJson)
        val rate = AdapterJson.parseOpenAiCompatibleError(providerId, rateJson)
        val billing = AdapterJson.parseOpenAiCompatibleError(providerId, billingJson)
        val malformed = AdapterJson.parseOpenAiCompatibleSuccess(providerId, malformedJson)
        val empty = AdapterJson.parseOpenAiCompatibleSuccess(providerId, "")

        return listOf(
            AdapterFixtureResult(providerId, "success", success is ProviderCallResult.Success && success.content == "fixture response", success.toString()),
            AdapterFixtureResult(providerId, "auth_failure", auth.type == NormalizedProviderFailureType.AUTH_FAILED, auth.toString()),
            AdapterFixtureResult(providerId, "rate_limit", rate.type == NormalizedProviderFailureType.RATE_LIMITED, rate.toString()),
            AdapterFixtureResult(providerId, "billing", billing.type == NormalizedProviderFailureType.BILLING_REQUIRED, billing.toString()),
            AdapterFixtureResult(providerId, "malformed", malformed is ProviderCallResult.Failure && malformed.failure.type == NormalizedProviderFailureType.MALFORMED_RESPONSE, malformed.toString()),
            AdapterFixtureResult(providerId, "empty", empty is ProviderCallResult.Failure && empty.failure.type == NormalizedProviderFailureType.EMPTY_RESPONSE, empty.toString())
        )
    }
}

private abstract class BaseKernelAdapter(
    final override val descriptor: ProviderDescriptor,
    private val env: Map<String, String> = System.getenv(),
    private val liveTransportImplemented: Boolean = false,
    private val defaultModel: String = "${descriptor.id}-default",
    private val modelIds: List<String> = listOf(defaultModel)
) : ProviderAdapter {
    private val normalizer = ProviderErrorNormalizer()

    override fun status(): AdapterStatus {
        val configured = descriptor.isLocal || descriptor.requiredEnv.all { env[it].isNullOrBlank().not() }
        val health = when {
            descriptor.isLocal -> "ready"
            !implemented() -> "contract_only"
            configured -> "kernel_ready"
            else -> "missing_key"
        }

        return AdapterStatus(
            providerId = descriptor.id,
            implemented = implemented(),
            configured = configured,
            dryRunOnly = !liveTransportImplemented,
            modelCount = modelIds.size,
            health = health,
            detail = when {
                descriptor.isLocal -> "local adapter"
                liveTransportImplemented -> "live transport available"
                implemented() -> "fixture-backed adapter kernel; live network opt-in deferred"
                else -> "descriptor registered; provider-specific schema pending"
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

        if (request.dryRun) {
            return ProviderCallResult.Success(
                providerId = descriptor.id,
                content = dryRunContent(request),
                usage = ProviderUsage(),
                model = modelIds.firstOrNull(),
                requestId = "dry-run-${descriptor.id}"
            )
        }

        if (!request.liveNetworkAllowed || !liveTransportImplemented) {
            return ProviderCallResult.Queued(
                task = request.task,
                earliestRetryEpochMs = System.currentTimeMillis() + 300_000L,
                reason = "${descriptor.id} live network disabled; dry-run kernel is available"
            )
        }

        return ProviderCallResult.Failure(
            normalizer.normalize(descriptor.id, "${descriptor.id} live transport unavailable")
        )
    }

    protected open fun implemented(): Boolean = false

    protected open fun dryRunContent(request: AdapterRequest): String =
        "adapter=${descriptor.id} kernel_dry_run task=${request.task.kind.name.lowercase(Locale.US)} model=${modelIds.firstOrNull() ?: "none"} deadline=${request.deadlineEpochMs}"
}

private class LocalKernelAdapter(
    descriptor: ProviderDescriptor
) : BaseKernelAdapter(
    descriptor = descriptor,
    liveTransportImplemented = true,
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
    descriptor: ProviderDescriptor
) : BaseKernelAdapter(
    descriptor = descriptor,
    liveTransportImplemented = false,
    defaultModel = "${descriptor.id}-free",
    modelIds = listOf("${descriptor.id}-free", "${descriptor.id}-fallback")
), ChatProviderAdapter, CodeProviderAdapter {
    override fun implemented(): Boolean = true

    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in capabilities &&
            capabilities.any { it == ApiCapability.CHAT || it == ApiCapability.CODE || it == ApiCapability.REPAIR || it == ApiCapability.PLAN }

    fun fixtureResults(): List<AdapterFixtureResult> =
        AdapterKernelFixtures.runAll(descriptor.id)
}

private class DescriptorOnlyKernelAdapter(
    descriptor: ProviderDescriptor
) : BaseKernelAdapter(
    descriptor = descriptor,
    liveTransportImplemented = false,
    defaultModel = "${descriptor.id}-descriptor",
    modelIds = emptyList()
), ChatProviderAdapter, CodeProviderAdapter, SearchProviderAdapter, StorageProviderAdapter, EdgeExecutionAdapter, AssetProviderAdapter {
    override fun implemented(): Boolean = false

    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in capabilities
}

fun buildKernelAdapter(descriptor: ProviderDescriptor): ProviderAdapter {
    val openAiCompatible = setOf("groq", "openrouter", "deepinfra", "siliconflow")
    return when {
        descriptor.isLocal || descriptor.id == "local" -> LocalKernelAdapter(descriptor)
        descriptor.id in openAiCompatible -> OpenAiCompatibleKernelAdapter(descriptor)
        else -> DescriptorOnlyKernelAdapter(descriptor)
    }
}
