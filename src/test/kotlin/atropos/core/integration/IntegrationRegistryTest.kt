package atropos.core.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntegrationRegistryTest {
    @Test
    fun `first party integrations have one canonical catalog`() {
        val sentry = IntegrationRegistry.requireRegistered("sentry")

        assertEquals("https", sentry.transport)
        assertTrue(sentry.capabilities.contains("repair_proposals"))
        assertEquals(setOf("github", "mcp", "sentry"), IntegrationRegistry.all().map { it.id }.toSet())
    }
}
