package atropos.core.platform

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

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
        
        // Operations contract semantics
        val result = wire.spawnProcess(listOf("echo", "semantics"))
        assertTrue(result.isSuccess)
        assertEquals("semantics\n", result.getOrThrow().stdout)
    }
}
