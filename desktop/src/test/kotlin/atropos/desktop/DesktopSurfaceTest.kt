/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.desktop

import atropos.core.platform.PlatformCapability
import atropos.core.platform.PlatformDescriptor
import atropos.core.platform.PlatformHealth
import atropos.core.platform.RuntimePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSurfaceTest {
    @Test
    fun `snapshot exposes shared platform health and capabilities`() {
        val descriptor = PlatformDescriptor(
            platform = RuntimePlatform.JVM_LINUX,
            name = "test-desktop",
            version = "test",
            capabilities = setOf(PlatformCapability.DISPLAY_OUTPUT, PlatformCapability.PERSISTENT_STORAGE),
        )
        val snapshot = DesktopSurfaceSnapshot.from(
            descriptor,
            PlatformHealth(platform = RuntimePlatform.JVM_LINUX),
        )

        assertEquals("test-desktop", snapshot.platform)
        assertEquals("healthy", snapshot.health)
        assertEquals(listOf("DISPLAY_OUTPUT", "PERSISTENT_STORAGE"), snapshot.capabilities)
        assertTrue(snapshot.capabilities.isNotEmpty())
    }
}
