package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderEnvironmentAliases
import java.net.HttpURLConnection
import java.util.Locale

internal abstract class NonOpenAiFreeKernelAdapter(
    descriptor: ProviderDescriptor,
    protected val spec: NonOpenAiProviderSpec,
    env: Map<String, String> = System.getenv()
) : BaseKernelAdapter(
    descriptor = descriptor,
    env = env,
    transportImplemented = true,
    defaultModel = spec.defaultModel,
    modelIds = spec.models
) {
    override fun implemented(): Boolean = true

    override fun status(): AdapterStatus {
        val configured = spec.requiredEnv.all { required ->
            ProviderEnvironmentAliases.names(required).any { env[it].isNullOrBlank().not() }
        }
        return AdapterStatus(
            providerId = descriptor.id,
            implemented = true,
            configured = configured,
            dryRunOnly = false,
            modelCount = spec.models.size,
            health = if (configured) "live_ready" else "needs_${spec.requiredEnv.joinToString("_").lowercase(Locale.US)}",
            detail = "${spec.displayName} ${spec.schema.name.lowercase(Locale.US)} adapter; live opt-in; model=${spec.defaultModel}"
        )
    }

    override fun dryRunContent(request: AdapterRequest): String =
        "provider=${descriptor.id} schema=${spec.schema.name.lowercase(Locale.US)} mode=dry_run model=${spec.defaultModel} prompt_chars=${request.prompt.length} deadline=${request.deadlineEpochMs}"

    protected fun missingSecret(): ProviderCallResult.Failure =
        ProviderCallResult.Failure(
            ProviderFailure(
                providerId = descriptor.id,
                type = NormalizedProviderFailureType.AUTH_FAILED,
                cleanSummary = "${descriptor.id} missing ${spec.requiredEnv.joinToString("+")}",
                terminal = true
            )
        )

    protected fun readResponse(connection: HttpURLConnection): Pair<Int, String> {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.use { input ->
            input.readNBytes(MAX_RESPONSE_BYTES + 1).also {
                require(it.size <= MAX_RESPONSE_BYTES) {
                    "${descriptor.id} response exceeded $MAX_RESPONSE_BYTES bytes"
                }
            }.toString(Charsets.UTF_8)
        }.orEmpty()
        return code to raw
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    }

    protected fun remainingMs(request: AdapterRequest): Int =
        (request.deadlineEpochMs - System.currentTimeMillis()).coerceIn(1_000, 60_000).toInt()
}
