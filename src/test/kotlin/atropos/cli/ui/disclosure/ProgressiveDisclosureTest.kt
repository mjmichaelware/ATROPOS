/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.disclosure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgressiveDisclosureTest {
    @Test
    fun `progressive facade preserves canonical row expansion`() {
        val row = DisclosureRow.collapsed(
            DisclosureRowKind.THINKING,
            DisclosureContent.of("summary", mapOf(DisclosureLevel.L1 to listOf("detail")))
        )
        val expanded = ProgressiveDisclosure.expand(row)
        assertNotNull(expanded)
        assertEquals(listOf("detail"), ProgressiveDisclosure.visible(expanded.row))
    }

    @Test
    fun `deep expansion retains every earlier level and exposes immutable evidence`() {
        val content = DisclosureContent.of(
            "summary",
            l1 = listOf("outline"),
            l2 = listOf("analysis"),
            l3 = listOf("decision"),
            l4 = listOf("evidence hash")
        )
        val row = DisclosureRow.collapsed(DisclosureRowKind.EVIDENCE, content)
        val l1 = row.expand()!!.row
        val l2 = l1.expand()!!.row
        val l3 = l2.expand()!!.row
        val l4 = l3.expand()!!.row

        assertTrue(ProgressiveDisclosure.visible(l4).containsAll(listOf("outline", "analysis", "decision", "evidence hash")))
        assertTrue(ProgressiveDisclosure.visible(l4).containsAll(ProgressiveDisclosure.visible(l3)))
    }
}
