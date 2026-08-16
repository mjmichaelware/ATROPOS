package atropos.core.platform

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
