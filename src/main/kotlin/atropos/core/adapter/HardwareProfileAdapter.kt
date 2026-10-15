package atropos.core.adapter

import java.io.File

data class HardwareProfile(
    val arch: String,
    val osName: String,
    val availableProcessors: Int,
    val maxMemoryBytes: Long
)

class HardwareProfileAdapter {
    fun getProfile(): HardwareProfile {
        val osArch = System.getProperty("os.arch").lowercase()
        val osName = System.getProperty("os.name")
        val processors = Runtime.getRuntime().availableProcessors()
        val maxMemory = Runtime.getRuntime().maxMemory()

        val normalizedArch = when (osArch) {
            "x86_64", "amd64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            "armv7l", "armv7", "arm" -> "armv7l"
            "riscv64" -> "riscv64"
            else -> osArch
        }

        var memoryBytes = maxMemory
        if (osName.lowercase().contains("linux")) {
            try {
                File("/proc/meminfo").readText().lineSequence().firstOrNull { it.startsWith("MemTotal:") }
                    ?.let { line ->
                        val kb = line.substringAfter("MemTotal:").trim().substringBefore("kB").trim().toLong()
                        memoryBytes = kb * 1024
                    }
            } catch (e: Exception) {}
        }

        return HardwareProfile(normalizedArch, osName, processors, memoryBytes)
    }

    fun getArch(): String = getProfile().arch
}