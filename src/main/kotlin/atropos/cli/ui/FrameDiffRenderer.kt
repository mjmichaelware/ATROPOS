/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.PrintStream

class FrameDiffRenderer(
    private val out: PrintStream
) {
    private var previousWidth = -1
    private var previousHeight = -1
    private var previousLines:
        Array<String>? = null

    fun render(frame: ScreenFrame) {
        val old = previousLines
        val force = old == null ||
            previousWidth != frame.width ||
            previousHeight != frame.height

        out.print("\u001B[?25l")

        if (force) {
            out.print("\u001B[H\u001B[2J")
        }

        frame.lines.forEachIndexed {
                index,
                line ->

            if (
                force ||
                old.getOrNull(index) != line
            ) {
                out.print(
                    "\u001B[${index + 1};1H"
                )
                out.print(line)
            }
        }

        if (frame.showCursor) {
            out.print(
                "\u001B[${frame.cursorY};" +
                    "${frame.cursorX}H"
            )
            out.print("\u001B[?25h")
        }

        out.flush()
        previousWidth = frame.width
        previousHeight = frame.height
        previousLines = frame.copyLines()
    }

    fun invalidate() {
        previousWidth = -1
        previousHeight = -1
        previousLines = null
    }

    fun restoreCursor() {
        out.print("\u001B[?25h\u001B[0m")
        out.flush()
    }
}
