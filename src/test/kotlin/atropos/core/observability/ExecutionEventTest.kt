/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalRecord
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Source Doc 3 §5.1: every visible event carries timestamp, role, provider,
 * task, requirement, source and state. The journal carried three of the seven,
 * so these fix the other four in place — and fix the round trip, because an
 * event whose provenance survives publication but not a restart is provenance
 * only for as long as nobody needs it.
 */
class ExecutionEventTest {

    private val at = Instant.parse("2026-08-13T10:00:00Z")

    private fun event(
        task: String? = "close atom 90",
        requirement: String? = "SD3#5.1@L66-66",
        source: String? = "src/main/kotlin/atropos/core/observability/EventPublisher.kt",
        provider: String? = "groq"
    ) = ExecutionEvent(
        sequence = 7,
        timestamp = at,
        role = ExecutionRole.WORKER,
        category = EventCategory.FILE_MUTATION,
        state = RunState.RUNNING,
        payload = "wrote EventPublisher.kt",
        provider = provider,
        task = task,
        requirement = requirement,
        source = source,
        runId = "run-1",
        territory = "src/main/kotlin/atropos/core/observability/",
        evidenceHash = "a".repeat(64)
    )

    @Test
    fun `an event with all seven fields is provenance-complete`() {
        assertTrue(event().provenanceComplete)
        assertEquals(emptyList(), event().missingProvenance())
    }

    @Test
    fun `a missing field is named rather than merely counted`() {
        val incomplete = event(requirement = null, source = null)

        assertFalse(incomplete.provenanceComplete)
        assertEquals(listOf("requirement", "source"), incomplete.missingProvenance())
    }

    @Test
    fun `provenance survives a round trip through the journal`() {
        val original = event()

        val restored = ExecutionEvent.fromJournalRecord(original.toJournalRecord())

        assertEquals(original.role, restored.role)
        assertEquals(original.state, restored.state)
        assertEquals(original.task, restored.task)
        assertEquals(original.requirement, restored.requirement)
        assertEquals(original.source, restored.source)
        assertEquals(original.territory, restored.territory)
        assertEquals(original.evidenceHash, restored.evidenceHash)
        assertEquals(original.payload, restored.payload, "the payload must survive intact")
    }

    @Test
    fun `a payload containing the marker text is not confused for provenance`() {
        val original = event().copy(payload = "the word prov appears here")

        val restored = ExecutionEvent.fromJournalRecord(original.toJournalRecord())

        assertEquals("the word prov appears here", restored.payload)
        assertEquals(ExecutionRole.WORKER, restored.role)
    }

    /**
     * Journals written before this type existed must still read. Inventing
     * values to make an old record look compliant would flatter the
     * trace-completeness metric with data that was never recorded.
     */
    @Test
    fun `a record with no provenance decodes honestly rather than failing`() {
        val legacy = EventJournalRecord(
            sequence = 1,
            timestamp = at,
            category = EventCategory.STATUS,
            payload = "plain old payload",
            runId = "run-0",
            providerId = "groq"
        )

        val decoded = ExecutionEvent.fromJournalRecord(legacy)

        assertEquals("plain old payload", decoded.payload)
        assertEquals(ExecutionRole.SYSTEM, decoded.role)
        assertNull(decoded.requirement)
        assertFalse(decoded.provenanceComplete)
        assertEquals(listOf("task", "requirement", "source"), decoded.missingProvenance())
    }

    @Test
    fun `a damaged marker yields a readable event rather than a discarded one`() {
        val damaged = EventJournalRecord(
            sequence = 2,
            timestamp = at,
            category = EventCategory.ERROR,
            payload = "provrole=workertruncated here",
            runId = "run-0"
        )

        val decoded = ExecutionEvent.fromJournalRecord(damaged)

        assertTrue(decoded.payload.contains("truncated here"))
    }

    @Test
    fun `separators inside a field cannot shift the fields after it`() {
        val hostile = event(task = "a\u0002b\u0001C", requirement = "SD3#5.1@L66-66")

        val restored = ExecutionEvent.fromJournalRecord(hostile.toJournalRecord())

        assertEquals("SD3#5.1@L66-66", restored.requirement, "the next field must be intact")
        assertFalse(restored.task!!.contains('\u0002'))
        assertFalse(restored.task!!.contains('\u0001'))
    }

    @Test
    fun `an event with no provenance to add does not grow a marker`() {
        val plain = ExecutionEvent(
            sequence = 1,
            timestamp = at,
            role = ExecutionRole.SYSTEM,
            category = EventCategory.HEARTBEAT,
            state = RunState.IDLE,
            payload = "tick"
        )

        val restored = ExecutionEvent.fromJournalRecord(plain.toJournalRecord())
        assertEquals("tick", restored.payload)
    }

    @Test
    fun `render leads with who and what-now, not with the payload`() {
        val rendered = event().render()

        assertTrue(rendered.indexOf("worker") < rendered.indexOf("wrote EventPublisher"))
        assertTrue(rendered.contains("requirement=SD3#5.1@L66-66"))
    }

    @Test
    fun `an unknown role reads as system rather than throwing`() {
        assertEquals(ExecutionRole.SYSTEM, ExecutionRole.of("archduke"))
        assertEquals(ExecutionRole.SYSTEM, ExecutionRole.of(null))
        assertEquals(ExecutionRole.AUDITOR, ExecutionRole.of("AUDITOR"))
        assertEquals(ExecutionRole.HR_ROUTER, ExecutionRole.of("hr-router"))
    }

    @Test
    fun `executing roles are ordered by decreasing scope`() {
        assertEquals(
            listOf(
                ExecutionRole.DIRECTOR,
                ExecutionRole.DIVISION_VP,
                ExecutionRole.MANAGER,
                ExecutionRole.SPECIALIST,
                ExecutionRole.WORKER
            ),
            ExecutionRole.executing()
        )
    }
}
