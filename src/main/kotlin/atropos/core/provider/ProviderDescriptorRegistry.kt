package atropos.core.provider

interface ProviderDescriptorRegistry {
    fun getAll(): List<ProviderDescriptor>
    fun getById(id: String): ProviderDescriptor?
    fun getFreeEligible(): List<ProviderDescriptor>
    fun getPaidLocked(): List<ProviderDescriptor>
    fun getByCapability(capability: ApiCapability): List<ProviderDescriptor>
}
