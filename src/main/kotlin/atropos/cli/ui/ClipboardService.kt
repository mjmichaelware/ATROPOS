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
            val process: java.lang.Process = pb.start()
            process.waitFor()
            process.waitFor() == 0
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
            val process: java.lang.Process = pb.start()
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
     * Reads text from the system clipboard.
     * Returns null if clipboard is empty or unsupported.
     */
    fun paste(): String? {
        if (isTermux) {
            val text = pasteTermux()
            if (!text.isNullOrBlank()) return text
        }
        return when {
            osName.contains("linux") -> pasteLinux()
            osName.contains("mac") -> pasteMac()
            osName.contains("windows") -> pasteWindows()
            else -> pasteTermux()
        }
    }

    private fun pasteTermux(): String? {
        return runCatching {
            val process = ProcessBuilder("termux-clipboard-get").start()
            val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (process.waitFor() == 0 && text.isNotBlank()) text else null
        }.getOrNull()
    }

    private fun pasteLinux(): String? {
        return runCatching {
            val wayland = runCatching {
                val p = ProcessBuilder("wl-paste").start()
                val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                if (p.waitFor() == 0 && text.isNotBlank()) text else null
            }.getOrNull()
            if (wayland != null) return wayland

            val p = ProcessBuilder("xclip", "-selection", "clipboard", "-o").start()
            val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (p.waitFor() == 0 && text.isNotBlank()) text else null
        }.getOrNull()
    }

    private fun pasteMac(): String? {
        return runCatching {
            val p = ProcessBuilder("pbpaste").start()
            val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (p.waitFor() == 0 && text.isNotBlank()) text else null
        }.getOrNull()
    }

    private fun pasteWindows(): String? {
        return runCatching {
            val p = ProcessBuilder("powershell", "-command", "Get-Clipboard").start()
            val text = p.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            if (p.waitFor() == 0 && text.isNotBlank()) text else null
        }.getOrNull()
    }

    /**
     * Performs speech-to-text recognition via Android/Termux API.
     * Returns transcribed text or null on failure.
     */
    fun speechToText(): String? {
        return runCatching {
            val process = ProcessBuilder("termux-speech-to-text").start()
            val text = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.trim()
            if (process.waitFor() == 0 && text.isNotBlank()) text else null
        }.getOrNull()
    }

    /**
     * Opens native Android multi-line dialog with swipe-to-text and voice typing enabled.
     * Returns the composed text or null on cancel.
     */
    fun composeDialog(title: String = "Atropos Composer"): String? {
        return runCatching {
            val process = ProcessBuilder("termux-dialog", "text", "-t", title, "-m").start()
            val json = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.trim()
            if (process.waitFor() == 0 && json.isNotBlank()) {
                val textMarker = "\"text\":"
                val idx = json.indexOf(textMarker)
                if (idx >= 0) {
                    val after = json.substring(idx + textMarker.length).trim()
                    if (after.startsWith("\"")) {
                        val end = after.indexOf('"', 1)
                        if (end > 1) after.substring(1, end).replace("\\n", "\n").replace("\\\"", "\"")
                        else null
                    } else null
                } else null
            } else null
        }.getOrNull()
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