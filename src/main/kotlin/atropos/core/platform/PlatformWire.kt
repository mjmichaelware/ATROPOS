package atropos.core.platform

/**
 * Wires the [SharedPlatformContract] into existing platform adapters.
 * Connects the legacy [PlatformAbstraction] to the shared multiplatform boundary.
 */
class PlatformWire(
    private val abstraction: PlatformAbstraction = Platform.current
) : SharedPlatformContract {

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
}
