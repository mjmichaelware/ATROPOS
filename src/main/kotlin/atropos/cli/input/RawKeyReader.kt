/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

sealed class KeyEvent {
    data class Printable(val text: String) :
        KeyEvent()

    data class Paste(val text: String) :
        KeyEvent()

    data class UnknownEscape(val sequence: String) :
        KeyEvent()

    data class InvalidInput(val reason: String) :
        KeyEvent()

    object Enter : KeyEvent()
    object Tab : KeyEvent()
    object Backspace : KeyEvent()
    object Delete : KeyEvent()
    object Escape : KeyEvent()
    object CtrlC : KeyEvent()
    object CtrlD : KeyEvent()
    object ArrowLeft : KeyEvent()
    object ArrowRight : KeyEvent()
    object ArrowUp : KeyEvent()
    object ArrowDown : KeyEvent()
    object Home : KeyEvent()
    object End : KeyEvent()
    object ShiftTab : KeyEvent()
}

class RawKeyReader(
    input: InputStream,
    private val escapeTimeoutMillis: Long = 35,
    private val maximumPasteBytes: Int =
        1024 * 1024
) {
    private val bytes =
        LinkedBlockingQueue<Int>()

    private val pump = Thread(
        {
            try {
                while (true) {
                    val value = input.read()

                    if (value < 0) {
                        bytes.offer(END_OF_STREAM)
                        break
                    }

                    bytes.put(value)
                }
            } catch (_: Exception) {
                bytes.offer(INPUT_FAILURE)
            }
        },
        "atropos-key-reader"
    ).apply {
        isDaemon = true
        start()
    }

    fun readKey(): KeyEvent? {
        val value = takeByte()

        return when (value) {
            END_OF_STREAM -> null

            INPUT_FAILURE ->
                KeyEvent.InvalidInput(
                    "terminal input failed"
                )

            3 -> KeyEvent.CtrlC
            4 -> KeyEvent.CtrlD
            9 -> KeyEvent.Tab
            10, 13 -> KeyEvent.Enter
            8, 127 -> KeyEvent.Backspace
            27 -> parseEscape()

            in 32..126 ->
                KeyEvent.Printable(
                    value.toChar().toString()
                )

            in 128..255 ->
                parseUtf8(value)

            else ->
                KeyEvent.InvalidInput(
                    "unsupported control byte $value"
                )
        }
    }

    private fun parseEscape(): KeyEvent {
        val next = pollByte()

        if (next == null ||
            next == END_OF_STREAM
        ) {
            return KeyEvent.Escape
        }

        if (next == INPUT_FAILURE) {
            return KeyEvent.InvalidInput(
                "terminal input failed after escape"
            )
        }

        return when (next) {
            '['.code -> parseCsi()
            'O'.code -> parseSs3()

            else -> KeyEvent.UnknownEscape(
                "\u001B${next.toChar()}"
            )
        }
    }

    private fun parseCsi(): KeyEvent {
        val sequence = StringBuilder()

        repeat(MAX_ESCAPE_BYTES) {
            val value = pollByte()
                ?: return KeyEvent.UnknownEscape(
                    "\u001B[$sequence"
                )

            if (value < 0) {
                return KeyEvent.UnknownEscape(
                    "\u001B[$sequence"
                )
            }

            sequence.append(value.toChar())

            if (value in 0x40..0x7E) {
                val code = sequence.toString()

                return when (code) {
                    "A" -> KeyEvent.ArrowUp
                    "B" -> KeyEvent.ArrowDown
                    "C" -> KeyEvent.ArrowRight
                    "D" -> KeyEvent.ArrowLeft
                    "H", "1~", "7~" ->
                        KeyEvent.Home

                    "F", "4~", "8~" ->
                        KeyEvent.End

                    "3~" -> KeyEvent.Delete
                    "Z" -> KeyEvent.ShiftTab
                    "200~" -> parseBracketedPaste()

                    else ->
                        KeyEvent.UnknownEscape(
                            "\u001B[$code"
                        )
                }
            }
        }

        return KeyEvent.UnknownEscape(
            "\u001B[$sequence"
        )
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

            null, END_OF_STREAM ->
                KeyEvent.UnknownEscape("\u001BO")

            else ->
                KeyEvent.UnknownEscape(
                    "\u001BO${value.toChar()}"
                )
        }
    }

    private fun parseBracketedPaste(): KeyEvent {
        val output = ByteArrayOutputStream()
        var matched = 0

        while (output.size() <= maximumPasteBytes) {
            val value = takeByte()

            if (value == END_OF_STREAM) {
                return KeyEvent.InvalidInput(
                    "unterminated bracketed paste"
                )
            }

            if (value == INPUT_FAILURE) {
                return KeyEvent.InvalidInput(
                    "input failed during paste"
                )
            }

            if (value == PASTE_END[matched].toInt()) {
                matched++

                if (matched == PASTE_END.size) {
                    return decodePaste(
                        output.toByteArray()
                    )
                }

                continue
            }

            if (matched > 0) {
                output.write(
                    PASTE_END,
                    0,
                    matched
                )
                matched = 0

                if (value == PASTE_END[0].toInt()) {
                    matched = 1
                    continue
                }
            }

            output.write(value)
        }

        return KeyEvent.InvalidInput(
            "bracketed paste exceeded " +
                "$maximumPasteBytes bytes"
        )
    }

    private fun parseUtf8(
        firstByte: Int
    ): KeyEvent {
        val continuationCount = when {
            firstByte and 0xE0 == 0xC0 -> 1
            firstByte and 0xF0 == 0xE0 -> 2
            firstByte and 0xF8 == 0xF0 -> 3

            else -> return KeyEvent.InvalidInput(
                "invalid UTF-8 leading byte"
            )
        }

        val encoded =
            ByteArray(continuationCount + 1)

        encoded[0] = firstByte.toByte()

        for (index in 1 until encoded.size) {
            val value = takeByte()

            if (value !in 0x80..0xBF) {
                return KeyEvent.InvalidInput(
                    "invalid UTF-8 continuation byte"
                )
            }

            encoded[index] = value.toByte()
        }

        return decodePrintable(encoded)
    }

    private fun decodePaste(
        encoded: ByteArray
    ): KeyEvent {
        return when (
            val decoded = decodeUtf8(encoded)
        ) {
            null -> KeyEvent.InvalidInput(
                "paste contained invalid UTF-8"
            )

            else -> KeyEvent.Paste(decoded)
        }
    }

    private fun decodePrintable(
        encoded: ByteArray
    ): KeyEvent {
        return when (
            val decoded = decodeUtf8(encoded)
        ) {
            null -> KeyEvent.InvalidInput(
                "invalid UTF-8 sequence"
            )

            else -> KeyEvent.Printable(decoded)
        }
    }

    private fun decodeUtf8(
        encoded: ByteArray
    ): String? {
        return try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(
                    CodingErrorAction.REPORT
                )
                .onUnmappableCharacter(
                    CodingErrorAction.REPORT
                )
                .decode(
                    ByteBuffer.wrap(encoded)
                )
                .toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun takeByte(): Int {
        return try {
            bytes.take()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            INPUT_FAILURE
        }
    }

    private fun pollByte(): Int? {
        return try {
            bytes.poll(
                escapeTimeoutMillis,
                TimeUnit.MILLISECONDS
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            INPUT_FAILURE
        }
    }

    companion object {
        private const val END_OF_STREAM = -1
        private const val INPUT_FAILURE = -2
        private const val MAX_ESCAPE_BYTES = 32

        private val PASTE_END = byteArrayOf(
            27,
            '['.code.toByte(),
            '2'.code.toByte(),
            '0'.code.toByte(),
            '1'.code.toByte(),
            '~'.code.toByte()
        )
    }
}
