package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import java.util.Locale

internal class CloudflareWorkersKernelAdapter(
    descriptor: ProviderDescriptor,
    spec: NonOpenAiProviderSpec,
    env: Map<String, String> = System.getenv()
) : NonOpenAiFreeKernelAdapter(descriptor, spec, env), StorageProviderAdapter, EdgeExecutionAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in setOf(ApiCapability.EDGE, ApiCapability.STORAGE, ApiCapability.SECRET)

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        if (spec.requiredEnv.any { env[it].isNullOrBlank() }) return missingSecret()
        return ProviderCallResult.Queued(
            task = request.task,
            earliestRetryEpochMs = System.currentTimeMillis() + 300_000L,
            reason = "cloudflare worker deployment requires explicit deployment target"
        )
    }

    override fun dryRunContent(request: AdapterRequest): String =
        "provider=${descriptor.id} schema=cloudflare_workers mode=dry_run manifest=worker-module capability=${request.task.capability.name.lowercase(Locale.US)}"
}
