/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

import java.io.File

/**
 * ATOM 7.1: Dynamic Hardware Profile and Platform Discovery.
 *
 * Dynamically queries available processors, max heap allocations, and low-RAM thresholds
 * without hardcoding developer workstation assumptions.
 *
 * ## Research: sysconf and cgroup v2 memory.max Telemetry
 * Queries `/sys/fs/cgroup/memory.max` or `/sys/fs/cgroup/memory/memory.limit_in_bytes`
 * on Linux containers to detect hard container boundaries that constrain the JVM
 * below physical host hardware limits.
 */
object DynamicHardwareProfileDiscovery {

    fun discover(): PlatformDescriptor {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val jvmMaxMemory = Runtime.getRuntime().maxMemory()
        val cgroupLimit = readCgroupMemoryLimit()
        val effectiveMemory = if (cgroupLimit != null && cgroupLimit < jvmMaxMemory) cgroupLimit else jvmMaxMemory

        val isLowRam = effectiveMemory <= 1024L * 1024L * 1024L || cores <= 2
        val os = System.getProperty("os.name", "unknown").lowercase()

        val platform = when {
            os.contains("linux") -> RuntimePlatform.JVM_LINUX
            os.contains("mac") || os.contains("darwin") -> RuntimePlatform.JVM_MACOS
            os.contains("win") -> RuntimePlatform.JVM_WINDOWS
            else -> RuntimePlatform.UNKNOWN
        }

        return PlatformDescriptor(
            platform = platform,
            name = platform.name,
            version = System.getProperty("java.version", "unknown"),
            cpuCores = cores,
            totalMemoryBytes = effectiveMemory,
            isLowRamDevice = isLowRam
        )
    }

    private fun readCgroupMemoryLimit(): Long? {
        val cgroupV2 = File("/sys/fs/cgroup/memory.max")
        if (cgroupV2.canRead()) {
            val content = runCatching { cgroupV2.readText().trim() }.getOrNull()
            if (content != null && content != "max") {
                return content.toLongOrNull()
            }
        }

        val cgroupV1 = File("/sys/fs/cgroup/memory/memory.limit_in_bytes")
        if (cgroupV1.canRead()) {
            return runCatching { cgroupV1.readText().trim().toLongOrNull() }.getOrNull()
        }

        return null
    }
}
