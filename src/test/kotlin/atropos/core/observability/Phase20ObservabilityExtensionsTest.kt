/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.core.journal.EventCategory
import atropos.cli.ui.design.RunState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Phase20ObservabilityExtensionsTest {

    @Test
    fun `ExecutionEvent extensions resolve correctly`() {
        val event = ExecutionEvent(
            sequence = 42L,
            timestamp = Instant.now(),
            role = ExecutionRole.SYSTEM,
            category = EventCategory.FILE_MUTATION,
            state = RunState.COMPLETED,
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
    fun `Markdown and JSON CardRenderers format correctly`() {
        val card = OutputCard(
            kind = CardKind.COMMAND,
            title = "Run tests",
            body = "test output",
            sequence = 1L,
            language = "bash"
        )
        
        val markdown = MarkdownExporter().render(card)
        assertEquals("### Run tests\n```bash\ntest output\n```", markdown)

        val json = JsonExporter().render(card)
        assertEquals("{\"title\": \"Run tests\", \"content\": \"test output\"}", json)
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
