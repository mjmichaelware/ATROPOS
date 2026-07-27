package atropos.core.provider

class ProviderDescriptorReport(private val registry: ProviderDescriptorRegistry) {
    fun generate(): String =
        listOf(
            "provider descriptors: ${registry.getAll().size}",
            "free eligible: ${registry.getFreeEligible().size}",
            "paid locked: ${registry.getPaidLocked().size}"
        ).joinToString("\n")
}
