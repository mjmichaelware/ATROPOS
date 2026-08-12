package atropos.core.provider.adapter

import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import java.util.Locale

internal class LocalFallbackDataInfraAdapter(
    descriptor: ProviderDescriptor,
    spec: DataInfraProviderSpec,
    env: Map<String, String> = System.getenv()
) : DataInfraKernelAdapter(descriptor, spec, env),
    StorageProviderAdapter,
    EdgeExecutionAdapter,
    SearchProviderAdapter {
    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in spec.capabilities

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        if (spec.requiredEnv.any { env[it].isNullOrBlank() }) {
            return ProviderCallResult.LocalOnly(
                task = request.task,
                content = "${descriptor.id} optional remote not configured; using ${spec.localFallback}"
            )
        }

        return ProviderCallResult.Queued(
            task = request.task,
            earliestRetryEpochMs = System.currentTimeMillis() + 300_000L,
            reason = "${descriptor.id} remote operation queued; local fallback remains available"
        )
    }
}
