/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.core.policy.BoundedProcessRunner
import java.io.File
import java.io.PrintStream
import java.nio.file.Path

data class TerminalGeometry(
    val rows: Int,
    val columns: Int
)

fun interface TerminalGeometryProvider {
    fun read(): TerminalGeometry?
}

class SttyTerminalGeometryProvider :
    TerminalGeometryProvider {

    private val processRunner = BoundedProcessRunner()

    override fun read(): TerminalGeometry? {
        val tty = File("/dev/tty")
        if (!tty.canRead()) return null

        return runCatching {
            val result = processRunner.run(
                command = listOf("stty", "size"),
                directory = Path.of("/"),
                timeoutMillis = 250L,
                maxOutputBytes = 128,
                maxOutputLines = 4,
                inputRedirect = tty.toPath()
            )
            if (result.timedOut || result.launchError != null || result.exitCode != 0) return@runCatching null
            val parts = result.stdout.trim().split(Regex("\\s+"))
            val rows = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
            val columns = parts.getOrNull(1)?.toIntOrNull() ?: return@runCatching null
            TerminalGeometry(rows.coerceAtLeast(12), columns.coerceAtLeast(1))
        }.getOrNull()
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
