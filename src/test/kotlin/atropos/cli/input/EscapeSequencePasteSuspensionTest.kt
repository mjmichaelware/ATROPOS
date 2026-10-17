/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import atropos.core.observability.BoundedRenderingController
import atropos.core.observability.TerminalPasteSuspensionLock
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscapeSequencePasteSuspensionTest {

    @BeforeTest
    @AfterTest
    fun tearDown() {
        TerminalPasteSuspensionLock.reset()
    }

    @Test
    fun `parseBracketedPaste acquires suspension lock and decodes paste cleanly`() {
        val stream = ConcurrentLinkedQueue<Int>()
        // CSI sequence: 200~Hello ATROPOS\e[201~
        val bytes = "[200~Hello ATROPOS\u001B[201~".map { it.code }
        stream.addAll(bytes)

        var suspendedDuringPaste = false
        var settledCalled = false

        val parser = EscapeSequenceParser(
            pollByte = { stream.poll() },
            takeByte = {
                if (BoundedRenderingController.isPasteSuspended()) {
                    suspendedDuringPaste = true
                }
                stream.poll() ?: -1
            },
            settleBuffer = {
                settledCalled = true
            }
        )

        val event = parser.parseEscape()

        assertTrue(event is KeyEvent.Paste)
        assertEquals("Hello ATROPOS", event.text)
        assertTrue(suspendedDuringPaste, "Lock must be held while reading paste bytes")
        assertTrue(settledCalled, "Settle buffer hook must be called after paste end")
        assertTrue(BoundedRenderingController.isPasteSuspended(), "Lock must remain active during settle window")
    }

    @Test
    fun `RawKeyReader properly registers Enter for carriage return and CRLF`() {
        // Carriage return (13) followed by line feed (10) should produce single Enter
        val crlfStream = ByteArrayInputStream(byteArrayOf(13, 10, 'a'.code.toByte()))
        val reader1 = RawKeyReader(crlfStream)

        val key1 = reader1.readKey()
        assertEquals(KeyEvent.Enter, key1)

        val key2 = reader1.readKey()
        assertEquals(KeyEvent.Printable("a"), key2)

        // Bare carriage return (13) produces Enter
        val crStream = ByteArrayInputStream(byteArrayOf(13, 'b'.code.toByte()))
        val reader2 = RawKeyReader(crStream)

        val key3 = reader2.readKey()
        assertEquals(KeyEvent.Enter, key3)

        val key4 = reader2.readKey()
        assertEquals(KeyEvent.Printable("b"), key4)
    }
}
