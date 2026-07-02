package atropos.core.provider.adapter

import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderCallResult
import atropos.core.provider.ProviderDescriptor

interface ProviderAdapter {
    val descriptor: ProviderDescriptor
    val providerId: String get() = descriptor.id
    val capabilities: Set<ApiCapability> get() = descriptor.capabilities

    fun status(): AdapterStatus

    fun canHandle(request: AdapterRequest): Boolean =
        request.task.capability in capabilities

    fun complete(request: AdapterRequest): ProviderCallResult
}

interface ChatProviderAdapter : ProviderAdapter
interface CodeProviderAdapter : ProviderAdapter
interface EmbeddingProviderAdapter : ProviderAdapter
interface SearchProviderAdapter : ProviderAdapter
interface StorageProviderAdapter : ProviderAdapter
interface EdgeExecutionAdapter : ProviderAdapter
interface AssetProviderAdapter : ProviderAdapter
