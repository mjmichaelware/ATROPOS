/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import atropos.core.parity.SurfaceContract
import atropos.core.parity.SurfaceObservation
import kotlin.test.assertTrue

class SuperiorityAddendumTest {

    @Test
    fun `AnsiScheme compliance check forbids raw escapes outside scheme`() {
        AnsiScheme.assertNoRawEscapes("hello ${AnsiScheme.GREEN}world${AnsiScheme.RESET}")
        assertFailsWith<IllegalArgumentException> {
            AnsiScheme.assertNoRawEscapes("hello \u001B[33mworld") // yellow code is not in AnsiScheme list
        }
    }

    @Test
    fun `GlobalByteCeiling asserts size limits`() {
        GlobalByteCeiling.verifyWithinCeiling(50 * 1024 * 1024L)
        assertFailsWith<IllegalArgumentException> {
            GlobalByteCeiling.verifyWithinCeiling(101 * 1024 * 1024L)
        }
    }

    @Test
    fun `PathResolver resolves safely and forbids traversal`() {
        val root = Paths.get("/tmp/base")
        val resolved = PathResolver.resolveSafe(root, "src/main.kt")
        assertEquals(root.resolve("src/main.kt").toAbsolutePath(), resolved)

        assertFailsWith<IllegalArgumentException> {
            PathResolver.resolveSafe(root, "../escaping")
        }
    }

    @Test
    fun `ComputerUseBridge converts request correctly`() {
        val bridge = ComputerUseBridge()
        val req = bridge.convertRequest("caller", "inspect", listOf("src"), "desktop", "grant-1")
        assertEquals(atropos.core.integration.InboundSource.COMPUTER_USE, req.source)
        assertEquals("desktop", req.targetSurface)
    }

    @Test
    fun `SessionManager limits open tabs`() {
        val manager = SessionManager(maxTabs = 2)
        manager.openSession("tab1")
        manager.openSession("tab2")
        assertEquals(2, manager.getActiveCount())
        assertFailsWith<IllegalArgumentException> {
            manager.openSession("tab3")
        }
    }

    @Test
    fun `RecoveryRibbon renders tectonic state`() {
        val ribbon = RecoveryRibbon()
        assertTrue(ribbon.renderRibbon("clean").contains("clean"))
    }

    @Test
    fun `MentionFileParser parses at-mentions from prompt`() {
        val prompt = "please edit @src/main.kt and check @docs/spec.md"
        val files = MentionFileParser.parseMentions(prompt)
        assertEquals(listOf("src/main.kt", "docs/spec.md"), files)
    }

    @Test
    fun `TerritoryMonitor measures complexity counters`() {
        val monitor = TerritoryMonitor()
        val summary = monitor.measureComplexity(listOf("f1", "f2", "f3"))
        assertTrue(summary.contains("linearCost=3"))
        assertTrue(summary.contains("quadraticCost=9"))
    }

    @Test
    fun `SurfaceContract asserts parity through canonical owner`() {
        val report = SurfaceContract(
            listOf(
                SurfaceObservation("cli", listOf("p"), listOf("Working"), listOf("Verified"), mapOf("n" to true)),
                SurfaceObservation("web", listOf("p"), listOf("Working"), listOf("Verified"), mapOf("n" to true))
            )
        ).check()
        assertTrue(report.holds)
    }
}
