package atropos.cli.ui

import atropos.core.output.OutputMode
import atropos.core.output.OutputModeDetector

object TerminalText {
    const val EXIT_SUCCESS: Int = 0
    const val EXIT_CONTRACT_FAILURE: Int = 65
    const val EXIT_CORE_FAULT: Int = 70
    const val EXIT_TERRITORY_REFUSAL: Int = 77

    /**
     * SD7-T3-004 / SD7-R-093: Strict TTY discipline standards.
     * Evaluates whether stdout is connected to an interactive TTY.
     */
    fun isatty(): Boolean {
        val mode = OutputModeDetector.detect()
        if (mode == OutputMode.HEADLESS || mode == OutputMode.TERM_DUMB) return false
        return System.console() != null
    }

    /**
     * SD7-R-093: Pure stderr protocol for progress, spinners, and telemetry.
     */
    fun emitProgress(message: String) {
        System.err.println(message)
    }

    fun emitTelemetry(message: String) {
        System.err.println(message)
    }

    fun emitSpinner(frame: String, message: String) {
        System.err.print("\r$frame $message")
        System.err.flush()
    }

    fun emitArtifact(artifact: String) {
        System.out.println(artifact)
    }

    private val ansi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")

    fun stripAnsi(value: String): String = ansi.replace(value, "")

    fun sanitize(value: String): String =
        stripAnsi(value).map {
            when {
                it == '\t' -> ' '
                it == '\n' -> '\n'
                it.code < 32 || it.code == 127 -> ' '
                else -> it
            }
        }.joinToString("")

    fun cellWidth(value: String): Int =
        stripAnsi(value).codePoints().map(::codePointWidth).sum()

    fun clip(value: String, maximumCells: Int): String {
        if (maximumCells <= 0) return ""
        val clean = stripAnsi(value)
        val output = StringBuilder()
        var used = 0

        clean.codePoints().forEachOrdered { codePoint ->
            val size = codePointWidth(codePoint)
            if (used + size <= maximumCells) {
                output.appendCodePoint(codePoint)
                used += size
            }
        }

        return output.toString()
    }

    fun ellipsize(value: String, maximumCells: Int): String {
        if (cellWidth(value) <= maximumCells) return value
        if (maximumCells <= 1) return "…"
        return clip(value, maximumCells - 1) + "…"
    }

    fun padEnd(value: String, targetCells: Int): String {
        val missing = (targetCells - cellWidth(value)).coerceAtLeast(0)
        return value + " ".repeat(missing)
    }

    fun compactPath(path: String): String {
        val home = System.getProperty("user.home") ?: return path
        return if (home.isNotEmpty() && path.startsWith(home)) {
            "~" + path.removePrefix(home)
        } else {
            path
        }
    }

    private fun codePointWidth(codePoint: Int): Int =
        when {
            Character.getType(codePoint) ==
                Character.NON_SPACING_MARK.toInt() -> 0

            codePoint in 0x1100..0x115F ||
                codePoint in 0x2E80..0xA4CF ||
                codePoint in 0xAC00..0xD7A3 ||
                codePoint in 0x1F300..0x1FAFF -> 2

            else -> 1
        }
}
