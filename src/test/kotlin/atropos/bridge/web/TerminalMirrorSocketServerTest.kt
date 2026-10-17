/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.web

import org.junit.Test
import java.io.InputStream
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalMirrorSocketServerTest {

    @Test
    fun `server initializes with designated port and provides input stream`() {
        val server = TerminalMirrorSocketServer(18787)
        val input: InputStream = server.receiveInput()
        assertNotNull(input)

        // Broadcast should handle empty gracefully
        server.broadcastFrame(byteArrayOf())
        server.stop()
    }
}
