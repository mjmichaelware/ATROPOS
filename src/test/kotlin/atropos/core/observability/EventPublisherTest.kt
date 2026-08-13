/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Publishing is two writes that must not diverge. If a producer does one, the
 * live transcript and the durable history disagree about what happened — which
 * is worse than either being absent, because both look authoritative.
 */
class EventPublisherTest {

    private fun publisher(stream: ProvenanceStream): Pair<EventPublisher, EventJournalService> {
        val root = Files.createTempDirectory("atropos-events-")
        val journal = EventJournalService(repoRoot = root)
        return EventPublisher(journal = journal, stream = stream) to journal
    }

    @Test
    fun `one publish reaches both the journal and the stream`() {
        val stream = ProvenanceStream()
        val (publisher, journal) = publisher(stream)

        publisher.publish(
            runId = "run-1",
            role = ExecutionRole.WORKER,
            category = EventCategory.FILE_MUTATION,
            state = RunState.RUNNING,
            payload = "wrote a file",
            requirement = "SD3#5.1@L66-66"
        )

        assertEquals(1, stream.replay().size, "the live stream must have it")
        assertEquals(1, journal.readEvents("run-1").size, "the durable journal must have it")
    }

    @Test
    fun `a published event survives a restart with its provenance`() {
        val stream = ProvenanceStream()
        val (publisher, journal) = publisher(stream)

        publisher.publishForRequirement(
            runId = "run-1",
            requirement = "SD3#5.1@L66-66",
            role = ExecutionRole.AUDITOR,
            category = EventCategory.VERIFICATION,
            state = RunState.COMPLETE,
            payload = "gate passed",
            task = "verify atom 90",
            source = "DeterministicVerifier",
            provider = "local",
            evidenceHash = "b".repeat(64)
        )

        // Reading through the journal is what a restart does.
        val restored = journal.readEvents("run-1").map(ExecutionEvent::fromJournalRecord).single()

        assertEquals(ExecutionRole.AUDITOR, restored.role)
        assertEquals(RunState.COMPLETE, restored.state)
        assertEquals("SD3#5.1@L66-66", restored.requirement)
        assertEquals("verify atom 90", restored.task)
        assertEquals("DeterministicVerifier", restored.source)
        assertEquals("b".repeat(64), restored.evidenceHash)
        assertTrue(restored.provenanceComplete)
        assertEquals("gate passed", restored.payload)
    }

    /**
     * Source Doc 2 rule 9: redaction runs before logging, status, persistence
     * and model prompts. Doing it at the publisher is what makes that
     * mechanical rather than remembered by each producer.
     */
    @Test
    fun `a secret in the payload never reaches either write`() {
        val stream = ProvenanceStream()
        val (publisher, journal) = publisher(stream)
        val leak = "api_key=sk-abcdefghijklmnop"

        publisher.publish(
            runId = "run-1",
            role = ExecutionRole.PROVIDER,
            category = EventCategory.TOOL_CALL,
            state = RunState.RUNNING,
            payload = "calling with $leak"
        )

        val streamed = stream.replay().single()
        val persisted = journal.readEvents("run-1").single()

        assertFalse(streamed.payload.contains("sk-abcdefghijklmnop"), "stream leaked")
        assertFalse(persisted.payload.contains("sk-abcdefghijklmnop"), "journal leaked")
    }

    @Test
    fun `a secret in the source field is redacted too`() {
        val stream = ProvenanceStream()
        val (publisher, _) = publisher(stream)

        publisher.publish(
            runId = "run-1",
            role = ExecutionRole.WORKER,
            category = EventCategory.COMMAND,
            state = RunState.RUNNING,
            payload = "ran",
            source = "token=sk-zzzzzzzzzzzzzzzz"
        )

        assertFalse(stream.replay().single().source!!.contains("sk-zzzzzzzzzzzzzzzz"))
    }

    @Test
    fun `the returned event carries the sequence the journal assigned`() {
        val stream = ProvenanceStream()
        val (publisher, _) = publisher(stream)

        val first = publisher.publish("run-1", ExecutionRole.WORKER, EventCategory.STATUS, RunState.RUNNING, "one")
        val second = publisher.publish("run-1", ExecutionRole.WORKER, EventCategory.STATUS, RunState.RUNNING, "two")

        assertTrue(second.sequence > first.sequence, "sequence must advance, not repeat")
    }

    @Test
    fun `events from separate runs do not mix in the journal`() {
        val stream = ProvenanceStream()
        val (publisher, journal) = publisher(stream)

        publisher.publish("run-a", ExecutionRole.WORKER, EventCategory.STATUS, RunState.RUNNING, "a")
        publisher.publish("run-b", ExecutionRole.WORKER, EventCategory.STATUS, RunState.RUNNING, "b")

        assertEquals(1, journal.readEvents("run-a").size)
        assertEquals(1, journal.readEvents("run-b").size)
    }

    @Test
    fun `a subscriber sees the event with provenance already attached`() {
        val stream = ProvenanceStream()
        val (publisher, _) = publisher(stream)
        var seen: ExecutionEvent? = null
        stream.subscribe("probe") { seen = it }

        publisher.publishForRequirement(
            runId = "run-1",
            requirement = "SD3#5.1@L66-66",
            role = ExecutionRole.DIRECTOR,
            category = EventCategory.DAG,
            state = RunState.PLANNING,
            payload = "decomposed",
            task = "plan",
            source = "InternalExecutionDagSynthesizer",
            provider = "local"
        )

        assertTrue(seen!!.provenanceComplete, "a surface must not have to fetch provenance separately")
    }
}
