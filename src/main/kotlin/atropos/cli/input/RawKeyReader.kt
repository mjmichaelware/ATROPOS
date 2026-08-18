/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

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
    object CtrlR : KeyEvent()
    object CtrlT : KeyEvent()
    object CtrlTab : KeyEvent()
    object ArrowLeft : KeyEvent()
    object ArrowRight : KeyEvent()
    object ArrowUp : KeyEvent()
    object ArrowDown : KeyEvent()
    object Home : KeyEvent()
    object End : KeyEvent()
    object ShiftTab : KeyEvent()

    /**
     * Scrollback movement.
     *
     * The transcript has had `scrollUp`, `scrollDown` and `followTail` since
     * it was written, and nothing could reach them: no key produced these
     * events, so output that scrolled past was gone. On a phone, where the
     * viewport is a dozen rows, that meant most of a run was unreadable.
     */
    object PageUp : KeyEvent()
    object PageDown : KeyEvent()
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

    private val escapeParser = EscapeSequenceParser(
        pollByte = { this.pollByte() },
        takeByte = { this.takeByte() }
    )

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
            18 -> KeyEvent.CtrlR
            20 -> KeyEvent.CtrlT
            9 -> KeyEvent.Tab
            10, 13 -> KeyEvent.Enter
            8, 127 -> KeyEvent.Backspace
            27 -> escapeParser.parseEscape()

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
    }
}
