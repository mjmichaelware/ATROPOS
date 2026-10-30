package atropos.core.adapter

import java.io.File

data class HardwareProfile(
    val arch: String,
    val osName: String = System.getProperty("os.name") ?: "unknown",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val totalMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    val availableProcessors: Int = cpuCores,
    val maxMemoryBytes: Long = totalMemoryBytes
)

class HardwareProfileAdapter {
    fun getProfile(): HardwareProfile {
        val rawArch = System.getProperty("os.arch", "").lowercase().trim()
        val osName = System.getProperty("os.name", "unknown")
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val maxMemory = Runtime.getRuntime().maxMemory()

        val normalizedArch = when (rawArch) {
            "x86_64", "amd64", "ia64" -> "x86_64"
            "aarch64", "arm64", "armv8", "armv8l", "armv8b" -> "arm64"
            "armv7l", "armv7", "armv6", "arm" -> "arm"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            "riscv64" -> "riscv64"
            else -> if (rawArch.isNotBlank()) rawArch else "portable"
        }

        var memoryBytes = maxMemory
        if (osName.lowercase().contains("linux") || File("/proc/meminfo").exists()) {
            try {
                File("/proc/meminfo").readText().lineSequence().firstOrNull { it.startsWith("MemTotal:") }
                    ?.let { line ->
                        val kb = line.substringAfter("MemTotal:").trim().substringBefore("kB").trim().toLong()
                        memoryBytes = kb * 1024
                    }
            } catch (_: Exception) {}
        }

        return HardwareProfile(
            arch = normalizedArch,
            osName = osName,
            cpuCores = cpuCores,
            totalMemoryBytes = memoryBytes,
            availableProcessors = cpuCores,
            maxMemoryBytes = memoryBytes
        )
    }

    fun getArch(): String = getProfile().arch
}
