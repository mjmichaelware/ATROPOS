package atropos.cli.ui

import atropos.core.provider.CostMode
import atropos.core.provider.ProviderDescriptorRegistry

class StatusProviderDescriptorRenderer(private val registry: ProviderDescriptorRegistry) {
    fun render(): String {
        val out = mutableListOf<String>()
        out += "PROVIDER DESCRIPTORS"
        out += "total: ${registry.getAll().size}"
        out += "free eligible: ${registry.getFreeEligible().size}"
        out += "paid locked: ${registry.getPaidLocked().size}"
        registry.getAll().sortedWith(compareBy({ it.costMode.ordinal }, { it.quotaTier }, { it.id })).forEach { d ->
            val state = when (d.costMode) {
                CostMode.LOCAL -> "local"
                CostMode.FREE -> "free"
                CostMode.COOLDOWN_OK -> "cooldown_ok"
                CostMode.CREDIT_POOL -> "credit_pool"
                CostMode.OPTIONAL_FREE -> "optional_free"
                CostMode.PAID_LOCKED -> "paid_locked"
            }
            out += "  ${state.padEnd(13)} q=${d.quotaTier} ${d.id.padEnd(18)} caps=${d.capabilities.size} env=${d.requiredEnv.size} endpoint=${d.endpointId ?: "no-endpoint"}"
        }
        return out.joinToString("\n")
    }
}
