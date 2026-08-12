/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerminalEvidenceLinkerTest {
    private val linker = TerminalEvidenceLinker()

    @Test
    fun linkOutput() {
        val output = "BUILD SUCCESSFUL"
        val link = linker.linkOutput(output, "evidence-123", 0)

        assertNotNull(link, "Should return a link")
        assertEquals("evidence-123", link.evidenceId, "Should preserve evidence ID")
        assertEquals(0..0, link.lineRange, "Should track line range")
    }

    @Test
    fun retrieveLink() {
        val output = "BUILD SUCCESSFUL"
        val link = linker.linkOutput(output, "evidence-123", 0)

        val retrieved = linker.getLink(link.outputHash)
        assertNotNull(retrieved, "Should retrieve stored link")
        assertEquals("evidence-123", retrieved.evidenceId)
    }

    @Test
    fun renderWithEvidence() {
        val output = "SUCCESS"
        val link = linker.linkOutput(output, "ev-456", 0)
        val rendered = linker.renderWithEvidence(output, link)

        assertTrue(rendered.contains("evidence: ev-456"), "Should include evidence ID")
        assertTrue(rendered.contains(link.outputHash.take(8)), "Should include hash fingerprint")
    }

    @Test
    fun hashConsistency() {
        val output = "SAME OUTPUT"
        val link1 = linker.linkOutput(output, "ev1", 0)
        val link2 = linker.linkOutput(output, "ev2", 5)

        assertEquals(link1.outputHash, link2.outputHash, "Same output should produce same hash")
    }
}
