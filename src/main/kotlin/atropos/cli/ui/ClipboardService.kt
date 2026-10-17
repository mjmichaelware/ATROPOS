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
     * Uses termux-dialog speech first for full native dialog UI, falling back to termux-speech-to-text.
     * Returns transcribed text or null on failure/cancel.
     */
    fun speechToText(): String? {
        val dialogSpeech = runCatching {
            val process = ProcessBuilder("termux-dialog", "speech", "-t", "Atropos Voice Input", "-i", "Speak now...").start()
            val json = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.trim()
            if (process.waitFor() == 0 && json.isNotBlank()) {
                extractDialogText(json)
            } else null
        }.getOrNull()

        if (!dialogSpeech.isNullOrBlank()) return dialogSpeech

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
    fun composeDialog(
        title: String = "Atropos Composer (Swipe & Voice)",
        hint: String = "Type, swipe, or use keyboard mic..."
    ): String? {
        return runCatching {
            val process = ProcessBuilder("termux-dialog", "text", "-t", title, "-i", hint, "-m").start()
            val json = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.trim()
            if (process.waitFor() == 0 && json.isNotBlank()) {
                extractDialogText(json)
            } else null
        }.getOrNull()
    }

    internal fun extractDialogText(json: String): String? {
        val codeMatch = Regex("\"code\"\\s*:\\s*(-?\\d+)").find(json)
        val code = codeMatch?.groupValues?.get(1)?.toIntOrNull()
        if (code != null && code != -1) return null

        val textIndex = json.indexOf("\"text\"")
        if (textIndex < 0) return null
        val colonIndex = json.indexOf(':', textIndex)
        if (colonIndex < 0) return null
        val quoteStart = json.indexOf('"', colonIndex)
        if (quoteStart < 0) return null

        val sb = StringBuilder()
        var i = quoteStart + 1
        var escaped = false
        while (i < json.length) {
            val c = json[i]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 4 < json.length) {
                            val hex = json.substring(i + 1, i + 5)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 4
                        }
                    }
                    else -> sb.append(c)
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '"') {
                return sb.toString().takeIf { it.isNotBlank() }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString().takeIf { it.isNotBlank() }
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