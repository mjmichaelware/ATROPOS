/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

class PlainTerminalOutput(
    val out: PrintStream =
        PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8),
    val errors: PrintStream =
        PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8)
) {
    private val outputLock = Any()
    val lock: Any get() = outputLock

    fun emitPlain(message: String, canvasWidth: Int = 80) {
        synchronized(outputLock) {
            val width = canvasWidth.coerceAtLeast(1)
            val plain = TerminalText.stripAnsi(message)
            val lines = plain.split('\n').flatMap { AnsiLineWrapper.wrap(it, width) }
            lines.forEach(out::println)
            out.flush()
        }
    }

    fun emitError(message: String) {
        errors.println("error: $message")
        errors.flush()
    }
}
