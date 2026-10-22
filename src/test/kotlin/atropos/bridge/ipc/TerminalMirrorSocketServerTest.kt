/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.ipc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TerminalMirrorSocketServerTest {

    @Test
    fun `frame types have correct binary opcodes`() {
        assertEquals(0x01.toByte(), IpcFrameType.OUTPUT.opcode)
        assertEquals(0x02.toByte(), IpcFrameType.INPUT.opcode)
        assertEquals(0x03.toByte(), IpcFrameType.RESIZE.opcode)
        assertEquals(0x00.toByte(), IpcFrameType.PING.opcode)

        assertEquals(IpcFrameType.OUTPUT, IpcFrameType.fromOpcode(0x01.toByte()))
        assertEquals(IpcFrameType.INPUT, IpcFrameType.fromOpcode(0x02.toByte()))
        assertEquals(IpcFrameType.RESIZE, IpcFrameType.fromOpcode(0x03.toByte()))
        assertEquals(IpcFrameType.PING, IpcFrameType.fromOpcode(0x00.toByte()))
    }

    @Test
    fun `server instantiates and shuts down cleanly`() {
        val server = TerminalMirrorSocketServer(bindPort = 18787)
        assertNotNull(server)
        server.shutdown()
    }
}
