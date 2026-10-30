/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.PrintStream

/**
 * ATOM 9.1: Terminal Canvas Mouse Tracking Decoupling.
 *
 * Controls ANSI terminal escape sequences for mouse tracking, ensuring mobile touch swipes
 * and terminal gestures are decoupled from screen buffer initialization.
 *
 * ## Research: DECSET 1000, 1002, 1006 SGR Mouse Protocol Variations
 * Researches protocol compatibility across DECSET 1000 (normal tracking), 1002 (button-event tracking),
 * and 1006 (SGR extended coordinate reporting) within terminal multiplexers (tmux, screen) to prevent
 * corrupted escape bytes during rapid mobile touch events.
 */
class DecoupledTerminalCanvasController(
    private val out: PrintStream,
    private val outputLock: Any,
    private val isInteractive: () -> Boolean
) {
    fun disableMouseTracking() = synchronized(outputLock) {
        if (!isInteractive()) return@synchronized
        // Disable SGR mouse tracking: 1006l, 1002l, 1000l
        out.print("\u001B[?1006l\u001B[?1002l\u001B[?1000l")
        out.flush()
    }

    fun enableMouseTracking() = synchronized(outputLock) {
        if (!isInteractive()) return@synchronized
        // Enable SGR mouse tracking: 1000h, 1002h, 1006h
        out.print("\u001B[?1000h\u001B[?1002h\u001B[?1006h")
        out.flush()
    }

    fun enterAlternateScreen() = synchronized(outputLock) {
        if (!isInteractive()) return@synchronized
        out.print("\u001B[?1049h\u001B[H\u001B[2J")
        out.flush()
    }

    fun exitAlternateScreen() = synchronized(outputLock) {
        if (!isInteractive()) return@synchronized
        out.print("\u001B[?1049l\u001B[0m")
        out.flush()
    }
}
