package atropos.core.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegrationRegistryTest {
    @Test
    fun `first party integrations have one canonical catalog`() {
        val sentry = IntegrationRegistry.requireRegistered("sentry")

        assertEquals("https", sentry.transport)
        assertTrue(sentry.capabilities.contains("repair_proposals"))
        assertEquals(
            setOf("github", "mcp", "filesystem", "git-local", "sentry"),
            IntegrationRegistry.all().map { it.id }.toSet()
        )
        assertEquals("stdio_or_http", IntegrationRegistry.requireRegistered("filesystem").transport)
        assertEquals("process", IntegrationRegistry.requireRegistered("git-local").transport)
    }
}
