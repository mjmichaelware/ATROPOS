/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.memory.MemoryLayerLattice
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderFailureMemoryStoreTest {

    private lateinit var store: ProviderFailureMemoryStore
    private lateinit var tempDir: java.nio.file.Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("provider-memory-test")
        val lattice = MemoryLayerLattice(tempDir)
        store = ProviderFailureMemoryStore(lattice)
    }

    @Test
    fun `first failure is recorded in Short-Term Memory with strike count 1`() {
        val mistake = store.recordMistake(
            provider = "groq",
            errorType = "SYNTAX_ERROR",
            exactMistake = "unclosed delimiter at char 42"
        )

        assertEquals("groq", mistake.provider)
        assertEquals(1, mistake.strikeCount)
        assertFalse(mistake.promotedToLongTerm)

        val stmMistakes = store.getShortTermMistakes("groq")
        assertEquals(1, stmMistakes.size)
        assertEquals(1, stmMistakes.first().strikeCount)
    }

    @Test
    fun `two strikes of exact same mistake escalate to permanent Long-Term Memory`() {
        // Strike 1
        val first = store.recordMistake(
            provider = "groq",
            errorType = "SYNTAX_ERROR",
            exactMistake = "unclosed delimiter at char 42"
        )
        assertEquals(1, first.strikeCount)
        assertFalse(first.promotedToLongTerm)
        assertEquals(0, store.getPermanentConstraints("groq").size)

        // Strike 2 (exact same mistake)
        val second = store.recordMistake(
            provider = "groq",
            errorType = "SYNTAX_ERROR",
            exactMistake = "unclosed delimiter at char 42"
        )
        assertEquals(2, second.strikeCount)
        assertTrue(second.promotedToLongTerm)

        val constraints = store.getPermanentConstraints("groq")
        assertEquals(1, constraints.size)
        assertTrue(constraints.first().contains("CRITICAL LONG-TERM CONSTRAINT"))
        assertTrue(constraints.first().contains("syntax_error"))
    }
}
