package atropos.core.platform

import java.nio.file.Path
import java.nio.file.Paths

enum class RuntimePlatform { JVM_LINUX, JVM_MACOS, JVM_WINDOWS, ANDROID, NATIVE_LINUX, NATIVE_MACOS, COMPOSE_DESKTOP, JS_BROWSER, UNKNOWN }

enum class PlatformCapability {
    FILESYSTEM_ACCESS, PROCESS_SPAWN, NETWORK_IO, ENVIRONMENT_VARS,
    COMPOSE_RENDERING, NATIVE_CODEC, GPU_ACCELERATION, DISPLAY_OUTPUT,
    PERSISTENT_STORAGE, TERMINAL_IO, USB_ACCESS, BLUETOOTH_ACCESS
}

data class PlatformDescriptor(
    val platform: RuntimePlatform,
    val name: String,
    val version: String,
    val osName: String = System.getProperty("os.name", "unknown"),
    val osArch: String = System.getProperty("os.arch", "unknown"),
    val javaVersion: String = System.getProperty("java.version", "unknown"),
    val capabilities: Set<PlatformCapability> = defaultCapabilities()
) {
    val isJvm: Boolean get() = platform.name.startsWith("JVM_")
    val isNative: Boolean get() = platform.name.startsWith("NATIVE_")
    val isDesktop: Boolean get() = platform in setOf(RuntimePlatform.JVM_LINUX, RuntimePlatform.JVM_MACOS, RuntimePlatform.JVM_WINDOWS, RuntimePlatform.COMPOSE_DESKTOP)

    companion object {
        fun detect(): PlatformDescriptor {
            val os = System.getProperty("os.name", "unknown").lowercase()
            val platform = when {
                os.contains("linux") -> RuntimePlatform.JVM_LINUX
                os.contains("mac") || os.contains("darwin") -> RuntimePlatform.JVM_MACOS
                os.contains("win") -> RuntimePlatform.JVM_WINDOWS
                else -> RuntimePlatform.UNKNOWN
            }
            return PlatformDescriptor(platform = platform, name = platform.name, version = System.getProperty("java.version", "unknown"))
        }

        fun defaultCapabilities(): Set<PlatformCapability> = setOf(
            PlatformCapability.FILESYSTEM_ACCESS,
            PlatformCapability.PROCESS_SPAWN,
            PlatformCapability.NETWORK_IO,
            PlatformCapability.ENVIRONMENT_VARS,
            PlatformCapability.PERSISTENT_STORAGE,
            PlatformCapability.TERMINAL_IO
        )
    }
}

data class PlatformPath(val segments: List<String>, val separator: String = "/") {
    val absolute: Boolean get() = segments.firstOrNull()?.isEmpty() == true || (segments.isNotEmpty() && segments.first().startsWith(separator))

    fun resolve(child: String): PlatformPath {
        val childSegments = child.split(separator).filter { it.isNotEmpty() && it != "." }
        return copy(segments = segments + childSegments)
    }

    fun toJavaPath(): Path = Paths.get(toString(), *emptyArray())

    override fun toString(): String = segments.joinToString(separator)
}

data class PlatformEnvironment(
    val platform: RuntimePlatform,
    val workDir: String = System.getProperty("user.dir", "/"),
    val tempDir: String = System.getProperty("java.io.tmpdir", "/tmp"),
    val homeDir: String = System.getProperty("user.home", "/root"),
    val pathSeparator: String = System.getProperty("path.separator", ":"),
    val fileSeparator: String = System.getProperty("file.separator", "/"),
    val availableMemoryMb: Long = Runtime.getRuntime().maxMemory() / (1024 * 1024),
    val availableCores: Int = Runtime.getRuntime().availableProcessors()
)

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
