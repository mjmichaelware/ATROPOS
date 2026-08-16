package atropos.core.platform

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test
import java.nio.file.Files

class PlatformWireTest {

    @Test
    fun `wire accurately exposes contract semantics from abstraction`() {
        val abstraction = JvmPlatformAbstraction()
        val wire = PlatformWire(abstraction)

        // Memory contract semantics
        val health = wire.checkHealth()
        assertNotNull(health)
        
        // Verification contract semantics
        val capabilities = wire.capabilities()
        assertTrue(capabilities.isNotEmpty())
        
        // Territory contract semantics
        val env = wire.environment()
        assertNotNull(env)

        val surfaces = wire.surfaces()
        assertEquals(1, surfaces.size)
        assertEquals(abstraction.descriptor.name.lowercase(), surfaces.single().id)
        assertTrue(surfaces.single().capabilities.isNotEmpty())
        
        // Operations contract semantics
        val result = wire.spawnProcess(listOf("echo", "semantics"))
        assertTrue(result.isSuccess)
        assertEquals("semantics\n", result.getOrThrow().stdout)
    }

    @Test
    fun `portable surface plan reports missing authority markers instead of guessing`() {
        val root = Files.createTempDirectory("atropos-portable-plan")
        val report = PortableSurfacePlan.inspect(root)
        assertTrue(report.missingMarkers.isNotEmpty())
        assertEquals(false, report.valid)
    }
}
