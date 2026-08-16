package atropos.core.acceptance

import atropos.core.agent.AgentContextCollector
import atropos.core.memory.LocalMemoryStore
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Implements SD3-071: Canonical acceptance tests.
 * Tests for context recognition, territory compliance, verification gates.
 */
class CanonicalAcceptanceTests {

    @Test
    fun `test final acceptance readiness evaluation`() {
        val tempDir = Files.createTempDirectory("acceptance-test-")
        try {
            val contextCollector = AgentContextCollector(repoRoot = tempDir)
            val memoryStore = LocalMemoryStore(tempDir.resolve("memory").toFile())
            val territoryStore = TerritoryStore(tempDir)
            val territoryService = TerritoryService(territoryStore)

            val acceptance = FinalSD1SD2Acceptance(
                contextCollector = contextCollector,
                memoryStore = memoryStore,
                territoryService = territoryService
            )

            // Evaluate readiness on empty temp directory (should be false due to empty context/territory)
            val result = acceptance.evaluateSD1SD2Readiness()
            assertFalse(result)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `test evaluation spec integration`() {
        val spec = EvaluationSpecIntegration()

        // A spec with no assertions has proved nothing, so it does not pass —
        // that is the same law that forbids a VERIFIED with no evidence, and
        // asserting the empty run passes would assert the opposite of it.
        assertFalse(spec.runSpec().passed)
        assertEquals(0.0, spec.runSpec().metrics.getValue("coverage"))

        val result = spec.runSpec(listOf(true, true))
        assertNotNull(result)
        assertTrue(result.passed)
        assertEquals(1.0, result.metrics.getValue("coverage"))
        assertFalse(spec.runSpec(listOf(true, false)).passed)
    }
}

