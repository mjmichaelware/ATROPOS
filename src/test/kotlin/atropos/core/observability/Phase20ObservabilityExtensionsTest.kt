/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.core.journal.EventCategory
import atropos.cli.ui.design.RunState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase20ObservabilityExtensionsTest {

    @Test
    fun `ExecutionEvent extensions resolve correctly`() {
        val event = ExecutionEvent(
            sequence = 42L,
            timestamp = Instant.now(),
            role = ExecutionRole.SYSTEM,
            category = EventCategory.FILE_MUTATION,
            state = RunState.COMPLETE,
            payload = "mutated file",
            provider = "agent-x",
            evidenceHash = "hash-1"
        )
        
        assertEquals("42", event.getId())
        assertEquals(EventKind.MUTATION, event.getKind())
        assertEquals("agent-x", event.getAgentId())
        assertEquals("mutated file", event.getContent())
        assertEquals(listOf("hash-1"), event.getHashes())
    }

    @Test
    fun `OutputCard extensions resolve correctly`() {
        val card = OutputCard(
            kind = CardKind.DIFF,
            title = "File diff",
            body = "some diff content",
            sequence = 1L,
            evidenceHash = "hash-diff",
            exitCode = 1
        )
        
        assertEquals("FAILED", card.status)
        assertEquals(listOf("hash-diff"), card.evidenceLinks)
        assertEquals("some diff content", card.content)
    }

    @Test
    fun `Markdown card rendering carries the title, the language and the body`() {
        val card = OutputCard(
            kind = CardKind.COMMAND,
            title = "Run tests",
            body = "test output",
            sequence = 1L,
            language = "bash"
        )

        // Asserted by structure rather than as one exact string: the renderer
        // also emits a metadata block, and pinning the whole output here would
        // make every added metadata field look like a rendering regression.
        val markdown = CardRenderer().renderMarkdown(card)
        assertTrue(markdown.startsWith("### Run tests"))
        assertTrue(markdown.contains("```bash"))
        assertTrue(markdown.contains("test output"))
    }

    @Test
    fun `ExecutionHistoryStore extensions resolve correctly`() {
        val store = ExecutionHistoryStore()
        val query = HistoryQuery(runId = "r1")
        // Just verify it doesn't crash on invocation
        val result = store.query(query)
        assertNotNull(result)
        
        val event = store.getById("-1")
        assertEquals(null, event) // -1 doesn't exist
    }
}
