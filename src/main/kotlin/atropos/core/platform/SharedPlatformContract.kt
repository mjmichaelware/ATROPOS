package atropos.core.platform

/**
 * A durable contract interface that all surfaces (CLI, Web, Desktop, Android) must expose.
 * Guarantees a unified foundation for the shared core engine across environments.
 */
interface SharedPlatformContract {
    /**
     * Operations: Exposes primitive functions needed to run standard workloads.
     */
    fun spawnProcess(command: List<String>, workingDir: String? = null): Result<ProcessOutput>

    /**
     * Memory: Exposes the available memory and current usage for the process.
     */
    fun checkHealth(): PlatformHealth

    /**
     * Verification: Reports the capabilities that this surface guarantees.
     */
    fun capabilities(): Set<PlatformCapability>

    /**
     * Territory: Provides bounded boundaries to standard local directories.
     */
    fun environment(): PlatformEnvironment
}
