/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

object TerminalText {
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
