package atropos.core.provider.adapter

import atropos.core.provider.NormalizedProviderFailureType
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import atropos.core.provider.ProviderErrorNormalizer
import atropos.core.provider.ProviderFailure
import atropos.core.provider.ProviderUsage
import java.util.Locale

internal abstract class BaseKernelAdapter(
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
