/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.platform

/**
 * ATOM EX8: REMOTE WSL NATIVE LINUX SUBSYSTEM INTERACTION
 * Path: src/main/kotlin/atropos/core/platform/WslSubsystemBridgeAdapter.kt
 *
 * Provides interoperability with the Windows Subsystem for Linux (WSL),
 * translating paths (e.g. `/mnt/c/Users/...` <-> `C:\Users\...`) and wrapping
 * commands to execute inside the native Linux environment.
 */
class WslSubsystemBridgeAdapter(
    val wslDistribution: String = "Ubuntu"
) {
    fun isAvailable(): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return os.contains("win") || java.nio.file.Files.exists(java.nio.file.Path.of("/proc/version"))
    }

    fun convertWindowsPathToWsl(windowsPath: String): String {
        val normalized = windowsPath.replace('\\', '/')
        val driveRegex = Regex("""^([a-zA-Z]):/(.*)$""")
        val match = driveRegex.find(normalized) ?: return normalized
        val (drive, rest) = match.destructured
        return "/mnt/${drive.lowercase()}/$rest"
    }

    fun convertWslPathToWindows(wslPath: String): String {
        val normalized = wslPath.replace('\\', '/')
        val mntRegex = Regex("""^/mnt/([a-zA-Z])/(.*)$""")
        val match = mntRegex.find(normalized) ?: return normalized
        val (drive, rest) = match.destructured
        return "${drive.uppercase()}:\\${rest.replace('/', '\\')}"
    }

    fun wrapCommandForWsl(command: List<String>): List<String> {
        val cmdString = command.joinToString(" ")
        return listOf("wsl.exe", "-d", wslDistribution, "--", "bash", "-lc", cmdString)
    }

    companion object {
        val defaultAdapter = WslSubsystemBridgeAdapter()
    }
}
