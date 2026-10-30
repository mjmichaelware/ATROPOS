/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.core.observability.BoundedRenderingController
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
        if (BoundedRenderingController.isPasteSuspended()) return null
        val tty = File("/dev/tty")
        if (!tty.canRead()) return null

        return runCatching {
            val result = processRunner.run(
                command = listOf("stty", "size"),
                directory = Path.of("/"),
                timeoutMillis = 250L,
                maxOutputBytes = 128,
                maxOutputLines = 4,
                inputRedirect = tty.toPath(),
                narrate = false
            )
            if (result.timedOut || result.launchError != null || result.exitCode != 0) return@runCatching null
            val parts = result.stdout.trim().split(Regex("\\s+"))
            val rows = parts.getOrNull(0)?.toIntOrNull() ?: return@runCatching null
            val columns = parts.getOrNull(1)?.toIntOrNull() ?: return@runCatching null
            TerminalGeometry(rows.coerceAtLeast(6), columns.coerceAtLeast(1))
        }.getOrNull()
    }
}

/**
 * Terminal canvas orchestrating geometry detection, alternate screens, and ANSI diff rendering.
 *
 * ## Research: DECSET 1000, 1002, 1006 SGR Mouse Protocol Variations
 * Research DECSET 1000, 1002, 1006 SGR mouse protocol variations across mobile terminal multiplexers
 * (tmux, screen, termux-x11) to isolate native touch-scroll gestures from mouse tracking bursts.
 */
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
        if (!capabilities.isInteractiveTerminal || BoundedRenderingController.isPasteSuspended()) {
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
        useAlternateScreen: Boolean,
        enableMouse: Boolean = false
    ) = synchronized(outputLock) {
        if (!capabilities.isInteractiveTerminal) {
            return@synchronized
        }

        refreshGeometry()

        if (useAlternateScreen) {
            out.print("\u001B[?1049h")
            alternateScreen = true
        }

        // Decouple mouse tracking so mobile touch swipes and terminal gestures
        // are not captured or corrupted by unsolicited SGR mouse reporting escapes
        if (enableMouse) {
            enableMouseTracking()
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

    fun renderFrame(frame: ScreenFrame) = render(frame)

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

    fun disableMouseTracking() = synchronized(outputLock) {
        if (!capabilities.isInteractiveTerminal) return@synchronized
        out.print("\u001B[?1006l\u001B[?1002l\u001B[?1000l")
        out.flush()
    }

    fun enableMouseTracking() = synchronized(outputLock) {
        if (!capabilities.isInteractiveTerminal) return@synchronized
        out.print("\u001B[?1000h\u001B[?1002h\u001B[?1006h")
        out.flush()
    }

    fun close() = synchronized(outputLock) {
        if (!capabilities.isInteractiveTerminal) {
            out.flush()
            return@synchronized
        }

        renderer.restoreCursor()

        // Disable SGR mouse tracking
        out.print("\u001B[?1006l\u001B[?1002l\u001B[?1000l")

        if (alternateScreen) {
            out.print("\u001B[?1049l")
            alternateScreen = false
        }

        out.print("\u001B[0m")
        out.flush()
        renderer.invalidate()
    }
}
