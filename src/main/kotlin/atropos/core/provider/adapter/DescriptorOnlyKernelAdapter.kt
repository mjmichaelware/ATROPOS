package atropos.core.provider.adapter

import atropos.core.provider.ProviderDescriptor

internal class DescriptorOnlyKernelAdapter(
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
