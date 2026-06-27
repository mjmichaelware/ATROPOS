/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class SttyResult(
    val exitCode: Int,
    val output: String
) {
    val successful: Boolean
        get() = exitCode == 0
}

fun interface SttyExecutor {
    fun execute(arguments: List<String>): SttyResult
}

class ProcessSttyExecutor(
    private val tty: File = File("/dev/tty")
) : SttyExecutor {
    override fun execute(arguments: List<String>): SttyResult {
        return try {
            val command = mutableListOf("stty")
            command.addAll(arguments)

            val process = ProcessBuilder(command)
                .redirectInput(tty)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(2, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                process.waitFor()
                return SttyResult(124, "stty timed out")
            }

            SttyResult(
                process.exitValue(),
                process.inputStream.bufferedReader()
                    .readText()
                    .trim()
            )
        } catch (failure: Exception) {
            SttyResult(
                1,
                failure.message ?: failure.javaClass.simpleName
            )
        }
    }
}

class TerminalModeManager(
    private val tty: File = File("/dev/tty"),
    private val stty: SttyExecutor =
        ProcessSttyExecutor(tty),
    private val ttyAvailable: () -> Boolean = {
        tty.exists() &&
            tty.canRead() &&
            tty.canWrite()
    },
    private val controlWriter: (String) -> Unit = {
        sequence ->
        FileOutputStream(tty).use { stream ->
            stream.write(sequence.toByteArray())
            stream.flush()
        }
    }
) : AutoCloseable {
    private val raw = AtomicBoolean(false)
    private var savedState: String? = null
    private var hookRegistered = false

    private val shutdownHook = Thread(
        { restore() },
        "atropos-terminal-restore"
    )

    val isRawModeEnabled: Boolean
        get() = raw.get()

    @Synchronized
    fun enableRawMode(): Boolean {
        if (raw.get()) return true
        if (!ttyAvailable()) return false

        val snapshot = stty.execute(listOf("-g"))
        if (!snapshot.successful ||
            snapshot.output.isBlank()
        ) {
            return false
        }

        savedState = snapshot.output
        registerShutdownHook()

        val result = stty.execute(
            listOf(
                "-icanon",
                "min", "1",
                "time", "0",
                "-echo",
                "-isig",
                "-ixon"
            )
        )

        if (!result.successful) {
            savedState = null
            unregisterShutdownHook()
            return false
        }

        raw.set(true)

        return try {
            controlWriter(ENABLE_BRACKETED_PASTE)
            true
        } catch (_: Exception) {
            restore()
            false
        }
    }

    @Synchronized
    fun restore(): Boolean {
        if (!raw.compareAndSet(true, false)) {
            return true
        }

        var successful = true

        try {
            controlWriter(DISABLE_BRACKETED_PASTE)
        } catch (_: Exception) {
            successful = false
        }

        val state = savedState

        if (state != null) {
            val result = stty.execute(listOf(state))
            successful = successful &&
                result.successful
        }

        savedState = null
        return successful
    }

    override fun close() {
        restore()
        unregisterShutdownHook()
    }

    private fun registerShutdownHook() {
        if (hookRegistered) return

        try {
            Runtime.getRuntime()
                .addShutdownHook(shutdownHook)
            hookRegistered = true
        } catch (_: IllegalStateException) {
        } catch (_: SecurityException) {
        }
    }

    private fun unregisterShutdownHook() {
        if (!hookRegistered) return

        try {
            Runtime.getRuntime()
                .removeShutdownHook(shutdownHook)
        } catch (_: IllegalStateException) {
        } catch (_: SecurityException) {
        } finally {
            hookRegistered = false
        }
    }

    companion object {
        private const val ENABLE_BRACKETED_PASTE =
            "\u001B[?2004h"

        private const val DISABLE_BRACKETED_PASTE =
            "\u001B[?2004l"
    }
}
