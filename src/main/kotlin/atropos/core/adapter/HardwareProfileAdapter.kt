/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.adapter

import java.io.File

/**
 * ATOM 1.1: Hardware Profile and Platform Detection Abstraction.
 *
 * Provides dynamic, unprivileged host hardware profiling across Linux, Android (Termux/bionic),
 * macOS, and container runtimes. Removes developer environment assumptions by querying runtime
 * system properties, `/proc/meminfo`, `/proc/cpuinfo`, and sysfs thermal/scaling interfaces.
 */
data class HardwareProfile(
    val arch: String,
    val osName: String = System.getProperty("os.name") ?: "unknown",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val totalMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
    val availableProcessors: Int = cpuCores,
    val maxMemoryBytes: Long = totalMemoryBytes,
    val simdExtensions: List<String> = emptyList(),
    val isThrottled: Boolean = false,
    val cpuFrequencyKhz: Long? = null
)

class HardwareProfileAdapter {

    /**
     * Resolves the active hardware profile dynamically from the host environment.
     * Guaranteed never to throw; unreadable kernel query paths gracefully fall back to safe defaults.
     */
    fun getProfile(): HardwareProfile {
        val rawArch = System.getProperty("os.arch", "").lowercase().trim()
        val osName = System.getProperty("os.name", "unknown")
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val jvmMaxMemory = Runtime.getRuntime().maxMemory()

        val normalizedArch = normalizeArchitecture(rawArch)
        val totalMemoryBytes = queryTotalMemory(osName, jvmMaxMemory)
        val simdExtensions = querySimdExtensions()
        val (isThrottled, cpuFreqKhz) = queryCpuThrottleAndFrequency()

        return HardwareProfile(
            arch = normalizedArch,
            osName = osName,
            cpuCores = cpuCores,
            totalMemoryBytes = totalMemoryBytes,
            availableProcessors = cpuCores,
            maxMemoryBytes = totalMemoryBytes,
            simdExtensions = simdExtensions,
            isThrottled = isThrottled,
            cpuFrequencyKhz = cpuFreqKhz
        )
    }

    /**
     * Returns the canonical normalized architecture identifier (arm64, x86_64, arm, x86, riscv64, or portable).
     */
    fun getArch(): String = getProfile().arch

    private fun normalizeArchitecture(rawArch: String): String = when (rawArch) {
        "x86_64", "amd64", "ia64" -> "x86_64"
        "aarch64", "arm64", "armv8", "armv8l", "armv8b" -> "arm64"
        "armv7l", "armv7", "armv6", "arm" -> "arm"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        "riscv64" -> "riscv64"
        else -> if (rawArch.isNotBlank()) rawArch else "portable"
    }

    private fun queryTotalMemory(osName: String, fallbackBytes: Long): Long {
        if (!osName.lowercase().contains("linux") && !File("/proc/meminfo").exists()) {
            return fallbackBytes
        }
        return runCatching {
            val memInfo = File("/proc/meminfo")
            if (memInfo.canRead()) {
                memInfo.useLines { lines ->
                    lines.firstOrNull { it.startsWith("MemTotal:") }
                        ?.let { line ->
                            val kb = line.substringAfter("MemTotal:")
                                .trim()
                                .substringBefore("kB")
                                .trim()
                                .toLong()
                            kb * 1024L
                        }
                } ?: fallbackBytes
            } else {
                fallbackBytes
            }
        }.getOrDefault(fallbackBytes)
    }

    private fun querySimdExtensions(): List<String> {
        val cpuInfo = File("/proc/cpuinfo")
        if (!cpuInfo.canRead()) return emptyList()

        return runCatching {
            val foundSimd = mutableSetOf<String>()
            val simdKeywords = setOf(
                "neon", "asimd", "fp", "aes", "sha1", "sha2", "crc32",
                "avx", "avx2", "avx512f", "avx512bw", "avx512cd", "sse",
                "sse2", "sse3", "ssse3", "sse4_1", "sse4_2"
            )

            cpuInfo.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("Features") || trimmed.startsWith("flags")) {
                        val flags = trimmed.substringAfter(":").trim().split(Regex("\\s+"))
                        for (flag in flags) {
                            val lower = flag.lowercase()
                            if (lower in simdKeywords) {
                                foundSimd.add(lower)
                            }
                        }
                    }
                }
            }
            foundSimd.sorted()
        }.getOrDefault(emptyList())
    }

    private fun queryCpuThrottleAndFrequency(): Pair<Boolean, Long?> {
        var throttled = false
        var freqKhz: Long? = null

        // Unprivileged sysfs thermal zone query
        runCatching {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.isDirectory) {
                thermalDir.listFiles { file -> file.name.startsWith("thermal_zone") }?.forEach { zone ->
                    val tempFile = File(zone, "temp")
                    if (tempFile.canRead()) {
                        val tempMilliC = tempFile.readText().trim().toLongOrNull()
                        // 80,000 mC = 80°C threshold for thermal throttling alert
                        if (tempMilliC != null && tempMilliC >= 80_000L) {
                            throttled = true
                        }
                    }
                }
            }
        }

        // Unprivileged sysfs cpufreq query
        runCatching {
            val curFreqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (curFreqFile.canRead()) {
                freqKhz = curFreqFile.readText().trim().toLongOrNull()
            } else {
                val altFreqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_cur_freq")
                if (altFreqFile.canRead()) {
                    freqKhz = altFreqFile.readText().trim().toLongOrNull()
                }
            }
        }

        return Pair(throttled, freqKhz)
    }
}
