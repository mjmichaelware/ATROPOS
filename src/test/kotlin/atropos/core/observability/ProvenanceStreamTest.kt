/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The stream's job is to reach every interested surface and to survive the ones
 * that break. A renderer failing to draw is cosmetic; the same exception
 * reaching the engine turns it into a lost run.
 */
class ProvenanceStreamTest {

    private fun event(sequence: Long, runId: String = "run-1", role: ExecutionRole = ExecutionRole.WORKER) =
        ExecutionEvent(
            sequence = sequence,
            timestamp = Instant.parse("2026-08-13T10:00:00Z"),
            role = role,
            category = EventCategory.STATUS,
            state = RunState.RUNNING,
            payload = "event $sequence",
            runId = runId,
            requirement = "SD3#5.1@L66-66"
        )

    @Test
    fun `a subscriber receives what it asked for`() {
        val stream = ProvenanceStream()
        val seen = mutableListOf<Long>()
        stream.subscribe("renderer") { seen += it.sequence }

        stream.emit(event(1))
        stream.emit(event(2))

        assertEquals(listOf(1L, 2L), seen)
    }

    @Test
    fun `an uninterested subscriber is never entered`() {
        val stream = ProvenanceStream()
        var entered = 0
        stream.subscribe("other-run", EventInterest.ofRun("run-2")) { entered++ }

        stream.emit(event(1, runId = "run-1"))

        assertEquals(0, entered, "filtering happens before the lambda, not inside it")
    }

    @Test
    fun `a subscriber that throws is recorded and skipped, not propagated`() {
        val stream = ProvenanceStream()
        val survivor = mutableListOf<Long>()
        stream.subscribe("broken") { error("renderer exploded") }
        stream.subscribe("survivor") { survivor += it.sequence }

        stream.emit(event(1))

        assertEquals(listOf(1L), survivor, "a later subscriber must still be reached")
        assertEquals(1, stream.subscriberFailures().size)
        assertEquals("broken", stream.subscriberFailures().first().subscriber)
        assertTrue(stream.subscriberFailures().first().reason.contains("renderer exploded"))
    }

    @Test
    fun `unsubscribing stops delivery without disturbing others`() {
        val stream = ProvenanceStream()
        val kept = mutableListOf<Long>()
        val stop = stream.subscribe("temporary") { error("must not run") }
        stream.subscribe("kept") { kept += it.sequence }

        stop()
        stream.emit(event(1))

        assertEquals(listOf(1L), kept)
        assertEquals(emptyList(), stream.subscriberFailures())
    }

    @Test
    fun `two surfaces may share a name without unsubscribing each other`() {
        val stream = ProvenanceStream()
        var first = 0
        var second = 0
        val stopFirst = stream.subscribe("cli") { first++ }
        stream.subscribe("cli") { second++ }

        stopFirst()
        stream.emit(event(1))

        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test
    fun `the tail is bounded so a long run cannot exhaust memory`() {
        val stream = ProvenanceStream(bound = 10)

        (1L..50L).forEach { stream.emit(event(it)) }

        val retained = stream.replay()
        assertEquals(10, retained.size)
        assertEquals(41L, retained.first().sequence, "the oldest are dropped, not the newest")
        assertEquals(50L, retained.last().sequence)
    }

    @Test
    fun `replay can be filtered the same way a subscription is`() {
        val stream = ProvenanceStream()
        stream.emit(event(1, runId = "run-1"))
        stream.emit(event(2, runId = "run-2"))
        stream.emit(event(3, runId = "run-1"))

        assertEquals(listOf(1L, 3L), stream.replay(EventInterest.ofRun("run-1")).map { it.sequence })
    }

    @Test
    fun `interest can select a role`() {
        val stream = ProvenanceStream()
        stream.emit(event(1, role = ExecutionRole.AUDITOR))
        stream.emit(event(2, role = ExecutionRole.WORKER))

        val audits = stream.replay(EventInterest(roles = setOf(ExecutionRole.AUDITOR)))
        assertEquals(listOf(1L), audits.map { it.sequence })
    }

    @Test
    fun `interest can select a requirement, which is what an evidence drawer needs`() {
        val stream = ProvenanceStream()
        stream.emit(event(1))
        stream.emit(event(2).copy(requirement = "SD3#4.1@L1-1"))

        assertEquals(
            listOf(1L),
            stream.replay(EventInterest.ofRequirement("SD3#5.1@L66-66")).map { it.sequence }
        )
    }

    @Test
    fun `who is listening can be reported so a stalled surface is nameable`() {
        val stream = ProvenanceStream()
        stream.subscribe("cli") {}
        stream.subscribe("bridge") {}

        assertEquals(listOf("cli", "bridge"), stream.subscribers())
    }

    @Test
    fun `the failure list is bounded too`() {
        val stream = ProvenanceStream()
        stream.subscribe("always-broken") { error("no") }

        repeat(ProvenanceStream.FAILURE_BOUND + 25) { stream.emit(event(it.toLong())) }

        assertEquals(ProvenanceStream.FAILURE_BOUND, stream.subscriberFailures().size)
    }
}
