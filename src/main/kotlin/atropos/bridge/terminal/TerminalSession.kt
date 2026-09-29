/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.terminal

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Terminal session backed by a PTY (pseudo-terminal).
 *
 * Spawns a shell in a PTY and provides read/write access to its stdin/stdout/stderr.
 * Uses the system's `script` command or `socat` for PTY allocation on Unix-like systems,
 * and falls back to a simple process pipe on other platforms.
 */
class TerminalSession(
    private val process: Process,
    private val stdin: OutputStream,
    private val stdout: InputStream,
    private val stderr: InputStream,
    private val alive: AtomicBoolean = AtomicBoolean(true)
) {
    /**
     * Reads from the terminal stdout/stderr.
     * Returns number of bytes read, or -1 if EOF.
     */
    fun read(buffer: ByteArray): Int {
        return try {
            val n = stdout.read(buffer)
            if (n > 0) return n
            val m = stderr.read(buffer)
            if (m > 0) return m
            -1
        } catch (e: IOException) {
            -1
        }
    }

    /**
     * Writes to the terminal stdin.
     * Returns number of bytes written.
     */
    fun write(data: ByteArray): Int {
        return try {
            stdin.write(data)
            stdin.flush()
            data.size
        } catch (e: IOException) {
            -1
        }
    }

    /**
     * Writes a string to the terminal stdin.
     */
    fun write(text: String): Int {
        return write(text.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Checks if the terminal is still alive.
     */
    fun isAlive(): Boolean = alive.get() && process.isAlive

    /**
     * Closes the terminal session.
     */
    fun close() {
        alive.set(false)
        try { stdin.close() } catch (_: Exception) {}
        try { stdout.close() } catch (_: Exception) {}
        try { stderr.close() } catch (_: Exception) {}
        process.destroy()
        try { process.waitFor() } catch (_: Exception) {}
    }

    companion object {
        /**
         * Spawns a new terminal session.
         * On Unix-like systems, uses `script` or `socat` for proper PTY allocation.
         * On Windows, falls back to a simple process pipe (no true PTY).
         */
        @Suppress("UNUSED_PARAMETER")
        fun spawn(projectId: String?): TerminalSession {
            return if (isUnix()) {
                spawnUnixPty()
            } else {
                spawnFallback()
            }
        }

        private fun isUnix(): Boolean {
            val os = System.getProperty("os.name").lowercase()
            return os.contains("linux") || os.contains("mac") || os.contains("unix")
        }

        private fun spawnUnixPty(): TerminalSession {
            // Try `script` command first (BSD/Linux), then `socat`, then fallback
            val commands = listOf(
                listOf("script", "-q", "-f", "-e", "/dev/null", "-c", getShell()),
                listOf("socat", "pty,raw,echo=0", "exec:'${SHELL:-bash}',pty,setsid,sigint,sane"),
                listOf(getShell())
            )

            for (cmd in commands) {
                try {
                    val pb = ProcessBuilder(*cmd.toTypedArray())
                    pb.redirectErrorStream(true)
                    val process = pb.start()
                    // Give the PTY a moment to initialize
                    Thread.sleep(100)
                    if (process.isAlive) {
                        return TerminalSession(process, process.outputStream, process.inputStream, process.errorStream)
                    }
                } catch (e: Exception) {
                    // Try next command
                }
            }
            // Fallback if all PTY methods fail
            return spawnFallback()
        }

        private fun spawnFallback(): TerminalSession {
            val shell = getShell()
            val pb = ProcessBuilder(shell)
            pb.redirectErrorStream(true)
            val process = pb.start()
            return TerminalSession(process, process.outputStream, process.inputStream, process.errorStream)
        }

        private fun getShell(): String {
            return System.getenv("SHELL") ?: System.getenv("COMSPEC") ?: "bash"
        }
    }
}