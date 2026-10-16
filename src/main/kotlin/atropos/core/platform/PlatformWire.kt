package atropos.core.platform

import atropos.core.platform.PlatformHealth

data class ThermalStatus(
    val temperatureCelsius: Double,
    val batteryPercentage: Double,
    val throttled: Boolean,
    val maxParallelWorkers: Int,
    val pauseHeavyBuilds: Boolean
)

/**
 * Wires the [SharedPlatformContract] into existing platform adapters.
 * Connects the legacy [PlatformAbstraction] to the shared multiplatform boundary.
 */
class PlatformWire(
    private val abstraction: PlatformAbstraction = Platform.current
) : SharedPlatformContract, atropos.core.shared.PortableSurfaceContract {
    private val hardwareProfile = atropos.core.adapter.HardwareProfileAdapter()

    override fun spawnProcess(command: List<String>, workingDir: String?): Result<ProcessOutput> {
        return abstraction.spawnProcess(command, workingDir)
    }

    override fun checkHealth(): PlatformHealth {
        return abstraction.health
    }

    /**
     * SD7-T2-022: Monitor battery level and temperature.
     * Throttle law: if temp>42°C or battery<20% reduce parallel workers and pause heavy builds.
     */
    fun evaluateThermalPacing(
        currentTempCelsius: Double = 35.0,
        currentBatteryPercent: Double = 80.0
    ): ThermalStatus {
        val shouldThrottle = currentTempCelsius > 42.0 || currentBatteryPercent < 20.0
        val workers = if (shouldThrottle) 2 else 64
        return ThermalStatus(
            temperatureCelsius = currentTempCelsius,
            batteryPercentage = currentBatteryPercent,
            throttled = shouldThrottle,
            maxParallelWorkers = workers,
            pauseHeavyBuilds = shouldThrottle
        )
    }

    override fun capabilities(): Set<PlatformCapability> {
        return abstraction.descriptor.capabilities
    }

    override fun environment(): PlatformEnvironment {
        return abstraction.environment
    }

    fun architecture(): String = hardwareProfile.getArch()

    fun descriptor(): PlatformDescriptor = abstraction.descriptor

    fun moduleTopology(root: java.nio.file.Path = atropos.core.AtroposRepoRootLocator.resolve()): PlatformModuleTopologyReport =
        PlatformModuleTopology.inspect(root)

    override fun surfaces(): List<atropos.core.shared.PortableSurface> = listOf(
        atropos.core.shared.PortableSurface(
            id = abstraction.descriptor.name.lowercase(),
            capabilities = abstraction.descriptor.capabilities.map { it.name.lowercase() }.toSet()
        )
    )
}
