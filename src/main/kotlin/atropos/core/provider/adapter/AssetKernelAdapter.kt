package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import java.util.Locale

internal class AssetKernelAdapter(
    descriptor: ProviderDescriptor,
    private val spec: AssetProviderSpec,
    env: Map<String, String> = System.getenv()
) : BaseKernelAdapter(
    descriptor = descriptor,
    env = env,
    transportImplemented = true,
    defaultModel = spec.defaultModel,
    modelIds = spec.models
), AssetProviderAdapter {
    override fun implemented(): Boolean = true

    override fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in setOf(ApiCapability.ASSET, ApiCapability.VISION)

    override fun status(): AdapterStatus {
        val configured = spec.requiredEnv.all { env[it].isNullOrBlank().not() }
        return AdapterStatus(
            providerId = descriptor.id,
            implemented = true,
            configured = configured,
            dryRunOnly = false,
            modelCount = spec.models.size,
            health = if (configured) "live_ready" else "optional_off",
            detail = "${spec.displayName} asset adapter; local SVG/Text/ANSI remains primary"
        )
    }

    override fun dryRunContent(request: AdapterRequest): String =
        "provider=${descriptor.id} schema=${spec.schema.name.lowercase(Locale.US)} mode=dry_run model=${spec.defaultModel} prompt_chars=${request.prompt.length}"

    override fun liveComplete(request: AdapterRequest): ProviderCallResult {
        if (spec.requiredEnv.any { env[it].isNullOrBlank() }) {
            return ProviderCallResult.LocalOnly(
                task = request.task,
                content = "${descriptor.id} optional asset provider not configured; use local asset generator"
            )
        }

        return ProviderCallResult.Queued(
            task = request.task,
            earliestRetryEpochMs = System.currentTimeMillis() + 300_000L,
            reason = "${descriptor.id} remote asset generation queued; local asset generator is primary"
        )
    }
}
