/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDurableStoreTest {

    @Test
    fun `DurableProjectStore saves and loads project data`() {
        val tempDir = Files.createTempDirectory("durable-store-").toFile()
        val store = DurableProjectStore(tempDir)
        store.saveProject("proj1", "{\"name\":\"Atropos\"}")
        assertEquals("{\"name\":\"Atropos\"}", store.loadProject("proj1"))
        tempDir.deleteRecursively()
    }

    @Test
    fun `TerminalEvidenceLinker returns linkage details`() {
        val link = TerminalEvidenceLinker.linkTerminalSession("term-1", "hash-1")
        assertTrue(link.contains("terminal=term-1"))
        assertTrue(link.contains("evidence=hash-1"))
    }

    @Test
    fun `AndroidHoeChrome renders title header`() {
        assertEquals("AndroidChromeHeader: ATROPOS", AndroidHoeChrome.renderChrome("ATROPOS"))
    }

    @Test
    fun `ColorIndependenceTest strips ANSI colors`() {
        val plain = "hello world"
        val colored = "\u001B[31mhello\u001B[0m world"
        assertTrue(ColorIndependenceTest.verifyColorIndependence(plain, colored))
    }

    @Test
    fun `InternalsDisclosure filters based on role`() {
        assertEquals("FULL_DISCLOSURE", InternalsDisclosure.getDisclosureLevel("L4_INTERNAL"))
        assertEquals("RESTRICTED", InternalsDisclosure.getDisclosureLevel("USER"))
    }
}
