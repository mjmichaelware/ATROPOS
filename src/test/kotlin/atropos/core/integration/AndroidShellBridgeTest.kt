/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidShellBridgeTest {

    @Test
    fun `AndroidHoeShell returns stdout signature`() {
        assertEquals("AndroidShell: ls", AndroidHoeShell.runShellCommand("ls"))
    }

    @Test
    fun `AtroposWebResolver handles absent directories gracefully`() {
        assertFalse(AtroposWebResolver.webDirExists())
    }

    @Test
    fun `HoeCliClipboardBridge gets and sets content`() {
        HoeCliClipboardBridge.copyToClipboard("text-data")
        assertEquals("text-data", HoeCliClipboardBridge.getClipboardContent())
    }

    @Test
    fun `TerritoryMaterializer renders list data`() {
        val out = TerritoryMaterializer.renderTerritoryMaterial(listOf("src", "docs"))
        assertTrue(out.contains("src,docs"))
    }

    @Test
    fun `AttestationFocusState produces output`() {
        assertEquals("OpticalFocus: sha256(123)", AttestationFocusState.getAttestationFocus("123"))
    }

    @Test
    fun `RecoveryTectonicRibbon shows restart count`() {
        assertEquals("TectonicRibbon: restarts=4", RecoveryTectonicRibbon.renderRibbon(4))
    }

    @Test
    fun `RethemeFromStatus selects correct color`() {
        assertEquals("DARK_RED", RethemeFromStatus.selectTheme("FAILED"))
        assertEquals("DARK_GREEN", RethemeFromStatus.selectTheme("SUCCESS"))
    }
}
