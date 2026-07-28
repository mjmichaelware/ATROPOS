package atropos.core.platform

data class PlatformHealth(
    val platform: RuntimePlatform,
    val heapUsedMb: Long = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
    val heapMaxMb: Long = Runtime.getRuntime().maxMemory() / (1024 * 1024),
    val threadCount: Int = Thread.activeCount(),
    val fileSystemWritable: Boolean = true,
    val processSpawnable: Boolean = true,
    val networkReachable: Boolean = false
) {
    val heapUsagePercent: Double = if (heapMaxMb > 0) (heapUsedMb.toDouble() / heapMaxMb.toDouble()) * 100.0 else 0.0
    val healthy: Boolean get() = fileSystemWritable && heapUsagePercent < 90.0
}
