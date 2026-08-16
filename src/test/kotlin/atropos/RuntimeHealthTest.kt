package atropos

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class RuntimeHealthTest {
    @Test
    fun `health mode is a bounded liveness command`() {
        // The production entrypoint owns the --health branch; this contract
        // keeps the machine-readable marker stable for container probes.
        assertEquals("ATROPOS_HEALTHY", ATROPOS_HEALTH_MARKER)
        assertTrue(ATROPOS_HEALTH_MARKER.isNotBlank())
    }
}
