/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

/**
 * ATOM CC2: REMOTE COMPUTE HARDWARE FLAVOR SELECTOR
 * Path: src/main/kotlin/atropos/core/platform/RemoteComputeHardwareFlavorSelector.kt
 *
 * Defines the hardware resource tier (CPU core count, RAM allocation, GPU acceleration)
 * for remote and cloud instances. Provides seamless scaling requests between lightweight
 * mobile companion nodes and heavy compilation servers.
 */
data class HardwareTierDescriptor(
    val id: String,
    val displayName: String,
    val cpuCores: Int,
    val totalMemoryBytes: Long,
    val gpuType: String? = null,
    val isDefault: Boolean = false,
    val hourlyCostCredits: Double = 0.0
)

class RemoteComputeHardwareFlavorSelector(
    private val availableTiers: List<HardwareTierDescriptor> = DEFAULT_TIERS
) {
    fun resolveFlavor(machineTypeKey: String?): HardwareTierDescriptor {
        if (machineTypeKey.isNullOrBlank()) {
            return availableTiers.firstOrNull { it.isDefault } ?: DEFAULT_TIERS[0]
        }
        val normalized = machineTypeKey.trim().lowercase()
        return availableTiers.firstOrNull {
            it.id.lowercase() == normalized || it.displayName.lowercase() == normalized
        } ?: availableTiers.firstOrNull { it.isDefault } ?: DEFAULT_TIERS[0]
    }

    fun listFlavors(): List<HardwareTierDescriptor> = availableTiers

    fun recommendTierForWorkload(requiresGpu: Boolean, memoryIntensiveCompilation: Boolean): HardwareTierDescriptor {
        return when {
            requiresGpu -> availableTiers.firstOrNull { it.gpuType != null } ?: DEFAULT_TIERS.last()
            memoryIntensiveCompilation -> availableTiers.firstOrNull { it.cpuCores >= 8 && it.totalMemoryBytes >= 32L * 1024 * 1024 * 1024 }
                ?: availableTiers.firstOrNull { it.cpuCores >= 8 }
                ?: DEFAULT_TIERS[1]
            else -> availableTiers.firstOrNull { it.isDefault } ?: DEFAULT_TIERS[0]
        }
    }

    companion object {
        val DEFAULT_TIERS = listOf(
            HardwareTierDescriptor(
                id = "standard-2core-8gb",
                displayName = "Standard Lightweight (2 vCPU, 8 GB RAM)",
                cpuCores = 2,
                totalMemoryBytes = 8L * 1024 * 1024 * 1024,
                isDefault = true,
                hourlyCostCredits = 0.18
            ),
            HardwareTierDescriptor(
                id = "standard-4core-16gb",
                displayName = "Standard Developer (4 vCPU, 16 GB RAM)",
                cpuCores = 4,
                totalMemoryBytes = 16L * 1024 * 1024 * 1024,
                hourlyCostCredits = 0.36
            ),
            HardwareTierDescriptor(
                id = "performance-8core-32gb",
                displayName = "Performance Build (8 vCPU, 32 GB RAM)",
                cpuCores = 8,
                totalMemoryBytes = 32L * 1024 * 1024 * 1024,
                hourlyCostCredits = 0.72
            ),
            HardwareTierDescriptor(
                id = "gpu-a10g",
                displayName = "GPU Accelerated (12 vCPU, 48 GB RAM, NVIDIA A10G 24GB)",
                cpuCores = 12,
                totalMemoryBytes = 48L * 1024 * 1024 * 1024,
                gpuType = "NVIDIA A10G 24GB",
                hourlyCostCredits = 1.95
            )
        )

        val defaultSelector = RemoteComputeHardwareFlavorSelector()
    }
}
