/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Clipboard service for Termux and desktop environments.
 *
 * F-CLI-008: Copy response card.
 * Uses Termux `termux-clipboard-set` on Android, `xclip`/`wl-copy` on Linux, `pbcopy` on macOS.
 */
class ClipboardService {
    private val osName = System.getProperty("os.name").lowercase()
    private val isTermux = System.getenv("TERMUX_VERSION") != null

    /**
     * Copies text to the system clipboard.
     * Returns true on success, false on failure.
     */
    fun copy(text: String): Boolean {
        if (isTermux) {
            return copyTermux(text)
        }
        return when {
            osName.contains("linux") -> copyLinux(text)
            osName.contains("mac") -> copyMac(text)
            osName.contains("windows") -> copyWindows(text)
            else -> false
        }
    }

    private fun copyTermux(text: String): Boolean {
        return runCatching {
            val pb = ProcessBuilder("termux-clipboard-set", text)
            pb.redirectErrorStream(true)
            val process = pb.start()
            process.waitFor()
            process.exitValue == 0
        }.getOrDefault(false)
    }

    private fun copyLinux(text: String): Boolean {
        return runCatching {
            // Try wl-copy first (Wayland), then xclip (X11)
            val wayland = ProcessBuilder("wl-copy", text).start()
            if (wayland.waitFor() == 0) return true
            val xclip = ProcessBuilder("xclip", "-selection", "clipboard", text).start()
            xclip.waitFor() == 0
        }.getOrDefault(false)
    }

    private fun copyMac(text: String): Boolean {
        return runCatching {
            val pb = ProcessBuilder("pbcopy")
            pb.redirectInput(ProcessBuilder.Redirect.PIPE)
            val process = pb.start()
            val writer = OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)
            writer.write(text)
            writer.flush()
            writer.close()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private fun copyWindows(text: String): Boolean {
        return runCatching {
            val pb = ProcessBuilder("powershell", "-command", "Set-Clipboard -Value \"$text\"")
            pb.start().waitFor() == 0
        }.getOrDefault(false)
    }

    /**
     * Checks if clipboard is available on the current platform.
     */
    fun isAvailable(): Boolean {
        return when {
            isTermux -> runCatching { ProcessBuilder("which", "termux-clipboard-set").start().waitFor() == 0 }.getOrDefault(false)
            System.getProperty("os.name").lowercase().contains("linux") -> runCatching { ProcessBuilder("which", "wl-copy").start().waitFor() == 0 }.getOrDefault(false) ||
                runCatching { ProcessBuilder("which", "xclip").start().waitFor() == 0 }.getOrDefault(false)
            System.getProperty("os.name").lowercase().contains("mac") -> runCatching { ProcessBuilder("which", "pbcopy").start().waitFor() == 0 }.getOrDefault(false)
            System.getProperty("os.name").lowercase().contains("windows") -> true
            else -> false
        }
    }
}