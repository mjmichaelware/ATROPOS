package atropos.core.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class PortableSurfaceContractTest {
    @Test
    fun `portable surface preserves deterministic identity and capabilities`() {
        val surface = PortableSurface("cli", setOf("commands", "evidence"))

        assertEquals("cli", surface.id)
        assertEquals(setOf("commands", "evidence"), surface.capabilities)
    }
}
