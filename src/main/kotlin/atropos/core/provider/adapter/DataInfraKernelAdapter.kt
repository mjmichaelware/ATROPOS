package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderFailure
import java.net.HttpURLConnection
import java.util.Locale

internal abstract class DataInfraKernelAdapter(
    descriptor: ProviderDescriptor,
    protected val spec: DataInfraProviderSpec,
    env: Map<String, String> = System.getenv()
) : BaseKernelAdapter(
    descriptor = descriptor,
    env = env,
    transportImplemented = true,
    defaultModel = spec.schema.name.lowercase(Locale.US),
    modelIds = listOf(spec.schema.name.lowercase(Locale.US), "local-fallback")
) {
    override fun implemented(): Boolean = true

    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in spec.capabilities

    override fun status(): AdapterStatus {
        val configured = spec.requiredEnv.all { env[it].isNullOrBlank().not() }
        return AdapterStatus(
            providerId = descriptor.id,
            implemented = true,
            configured = configured,
            dryRunOnly = false,
            modelCount = 2,
            health = if (configured) "live_ready" else "optional_off",
            detail = "${spec.displayName} ${spec.schema.name.lowercase(Locale.US)} adapter; local fallback=${spec.localFallback}"
        )
    }

    override fun dryRunContent(request: AdapterRequest): String =
        "provider=${descriptor.id} schema=${spec.schema.name.lowercase(Locale.US)} mode=dry_run fallback=${spec.localFallback} capability=${request.task.capability.name.lowercase(Locale.US)}"

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
        (request.deadlineEpochMs - System.currentTimeMillis()).coerceIn(1_000, 45_000).toInt()
}
