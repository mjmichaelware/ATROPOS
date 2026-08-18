/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

class EscapeSequenceParser(
    private val pollByte: () -> Int?,
    private val takeByte: () -> Int
) {
    private val MAX_ESCAPE_BYTES = 32
    private val PASTE_END = byteArrayOf(
        27,
        '['.code.toByte(),
        '2'.code.toByte(),
        '0'.code.toByte(),
        '1'.code.toByte(),
        '~'.code.toByte()
    )

    fun parseEscape(): KeyEvent {
        val next = pollByte()

        if (next == null || next == -1) {
            return KeyEvent.Escape
        }

        if (next == -2) {
            return KeyEvent.InvalidInput("terminal input failed after escape")
        }

        return when (next) {
            '['.code -> parseCsi()
            'O'.code -> parseSs3()
            else -> KeyEvent.UnknownEscape("${next.toChar()}")
        }
    }

    private fun parseCsi(): KeyEvent {
        val sequence = StringBuilder()

        repeat(MAX_ESCAPE_BYTES) {
            val value = pollByte()
                ?: return KeyEvent.UnknownEscape("[$sequence")

            if (value < 0) {
                return KeyEvent.UnknownEscape("[$sequence")
            }

            sequence.append(value.toChar())

            if (value in 0x40..0x7E) {
                val code = sequence.toString()

                return when (code) {
                    "A" -> KeyEvent.ArrowUp
                    "B" -> KeyEvent.ArrowDown
                    "C" -> KeyEvent.ArrowRight
                    "D" -> KeyEvent.ArrowLeft
                    "H", "1~", "7~" -> KeyEvent.Home
                    "F", "4~", "8~" -> KeyEvent.End
                    "3~" -> KeyEvent.Delete
                    // `5~`/`6~` are PageUp/PageDown; the `;2` and `;5` forms
                    // are shift- and ctrl-modified, which Termux's soft
                    // keyboard and most desktop terminals send interchangeably.
                    "5~", "5;2~", "5;5~" -> KeyEvent.PageUp
                    "6~", "6;2~", "6;5~" -> KeyEvent.PageDown
                    "Z" -> KeyEvent.ShiftTab
                    "9;5u", "27;5;9~", "1;5I" -> KeyEvent.CtrlTab
                    "200~" -> parseBracketedPaste()
                    else -> KeyEvent.UnknownEscape("[$code")
                }
            }
        }

        return KeyEvent.UnknownEscape("[$sequence")
    }

    private fun parseSs3(): KeyEvent {
        val value = pollByte()

        return when (value) {
            'A'.code -> KeyEvent.ArrowUp
            'B'.code -> KeyEvent.ArrowDown
            'C'.code -> KeyEvent.ArrowRight
            'D'.code -> KeyEvent.ArrowLeft
            'H'.code -> KeyEvent.Home
            'F'.code -> KeyEvent.End
            null, -1 -> KeyEvent.UnknownEscape("O")
            else -> KeyEvent.UnknownEscape("O${value.toChar()}")
        }
    }

    private fun parseBracketedPaste(): KeyEvent {
        val output = java.io.ByteArrayOutputStream()
        var matched = 0

        while (output.size() <= 1024 * 1024) {
            val value = takeByte()

            if (value == -1) {
                return KeyEvent.InvalidInput("unterminated bracketed paste")
            }

            if (value == -2) {
                return KeyEvent.InvalidInput("input failed during paste")
            }

            if (value == PASTE_END[matched].toInt()) {
                matched++

                if (matched == PASTE_END.size) {
                    return decodePaste(output.toByteArray())
                }

                continue
            }

            if (matched > 0) {
                output.write(PASTE_END, 0, matched)
                matched = 0

                if (value == PASTE_END[0].toInt()) {
                    matched = 1
                    continue
                }
            }

            output.write(value)
        }

        return KeyEvent.InvalidInput("bracketed paste exceeded 1048576 bytes")
    }

    private fun decodePaste(encoded: ByteArray): KeyEvent {
        return when (val decoded = decodeUtf8(encoded)) {
            null -> KeyEvent.InvalidInput("paste contained invalid UTF-8")
            else -> KeyEvent.Paste(decoded)
        }
    }

    private fun decodeUtf8(encoded: ByteArray): String? {
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(encoded)).toString()
        } catch (_: Exception) {
            null
        }
    }
}
