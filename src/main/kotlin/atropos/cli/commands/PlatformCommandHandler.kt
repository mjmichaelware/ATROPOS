/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.platform.Platform
import atropos.core.platform.PlatformAdapterRegistry

/**
 * `/platform` — Phase 18 host descriptor, health, and environment.
 *
 * Read-only throughout: every subcommand reports what the host currently is.
 * That is why the bare command is a descriptor line rather than a usage error —
 * there is nothing here an operator can trigger by accident.
 */
class PlatformCommandHandler {

    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "adapters" -> PlatformAdapterRegistry.renderAvailable()
        "health" -> health()
        "env" -> environment()
        else -> descriptor()
    }

    private fun health(): String {
        val health = Platform.health
        return buildString {
            appendLine("Platform health:")
            appendLine("  platform: ${health.platform}")
            appendLine(
                "  heap: ${health.heapUsedMb}/${health.heapMaxMb} MB " +
                    "(${"%.1f".format(health.heapUsagePercent)}%)"
            )
            appendLine("  threads: ${health.threadCount}")
            appendLine("  fs writable: ${health.fileSystemWritable}")
            appendLine("  network: ${health.networkReachable}")
            appendLine("  healthy: ${health.healthy}")
        }.trimEnd()
    }

    private fun environment(): String {
        val environment = Platform.environment
        return buildString {
            appendLine("Platform environment:")
            appendLine("  platform: ${environment.platform}")
            appendLine("  work dir: ${environment.workDir}")
            appendLine("  temp dir: ${environment.tempDir}")
            appendLine("  home dir: ${environment.homeDir}")
            appendLine("  memory: ${environment.availableMemoryMb} MB")
            appendLine("  cores: ${environment.availableCores}")
        }.trimEnd()
    }

    private fun descriptor(): String {
        val descriptor = Platform.descriptor
        return "Platform: ${descriptor.platform} ${descriptor.name} ${descriptor.version} " +
            "(${descriptor.osName} ${descriptor.osArch})"
    }
}
