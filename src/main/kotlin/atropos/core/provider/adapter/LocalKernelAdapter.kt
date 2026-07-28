package atropos.core.provider.adapter

import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor
import java.util.Locale

internal class LocalKernelAdapter(
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
