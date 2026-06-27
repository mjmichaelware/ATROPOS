/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import java.io.File
import java.io.PrintStream
import java.util.concurrent.TimeUnit

data class TerminalGeometry(
    val rows: Int,
    val columns: Int
)

fun interface TerminalGeometryProvider {
    fun read(): TerminalGeometry?
}

class SttyTerminalGeometryProvider :
    TerminalGeometryProvider {

    override fun read(): TerminalGeometry? {
        val tty = File("/dev/tty")
        if (!tty.canRead()) return null

        val process = ProcessBuilder(
            "stty",
            "size"
        )
            .redirectInput(tty)
            .redirectError(
                ProcessBuilder.Redirect.DISCARD
            )
            .start()

        if (
            !process.waitFor(
                250,
                TimeUnit.MILLISECONDS
            )
        ) {
            process.destroyForcibly()
            return null
        }

        if (process.exitValue() != 0) {
            return null
        }

        val parts =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
                .split(Regex("\\s+"))

        val rows = parts
            .getOrNull(0)
            ?.toIntOrNull()
            ?: return null
        val columns = parts
            .getOrNull(1)
            ?.toIntOrNull()
            ?: return null

        return TerminalGeometry(
            rows.coerceAtLeast(12),
            columns.coerceIn(36, 160)
        )
    }
}

class TerminalCanvas(
    private val capabilities:
        ConfigurationManager,
    private val out: PrintStream,
    private val outputLock: Any,
    private val geometryProvider:
        TerminalGeometryProvider =
            SttyTerminalGeometryProvider()
) {
    var width = 80
        private set
    var height = 24
        private set

    private val renderer =
        FrameDiffRenderer(out)
    private var alternateScreen = false

    fun refreshGeometry(): Boolean {
        if (!capabilities.isInteractiveTerminal) {
            return false
        }

        val oldWidth = width
        val oldHeight = height

        geometryProvider.read()?.let {
            width = it.columns
            height = it.rows
        }

        val changed =
            oldWidth != width ||
                oldHeight != height

        if (changed) renderer.invalidate()
        return changed
    }

    fun initialize(
        useAlternateScreen: Boolean
    ) = synchronized(outputLock) {
        if (!capabilities.isInteractiveTerminal) {
            return@synchronized
        }

        refreshGeometry()

        if (useAlternateScreen) {
            out.print("\u001B[?1049h")
            alternateScreen = true
        }

        out.print("\u001B[H\u001B[2J")
        out.flush()
        renderer.invalidate()
    }

    fun render(frame: ScreenFrame) =
        synchronized(outputLock) {
            if (
                capabilities.isInteractiveTerminal
            ) {
                renderer.render(frame)
            }
        }

    fun clearScreen() =
        synchronized(outputLock) {
            if (
                capabilities.isInteractiveTerminal
            ) {
                out.print("\u001B[H\u001B[2J")
                out.flush()
                renderer.invalidate()
            }
        }

    fun close() = synchronized(outputLock) {
        if (!capabilities.isInteractiveTerminal) {
            out.flush()
            return@synchronized
        }

        renderer.restoreCursor()

        if (alternateScreen) {
            out.print("\u001B[?1049l")
            alternateScreen = false
        }

        out.print("\u001B[0m")
        out.flush()
        renderer.invalidate()
    }
}
