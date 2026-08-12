package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentQueueStoreTest {
    @Test
    fun queue_record_round_trips_source_evidence_and_impacted_symbols() {
        val repoRoot = Files.createTempDirectory("atropos-agent-queue-store-")
        val store = AgentQueueStore(repoRoot)

        val created = store.createEntry(
            task = "phase 11 queue durability",
            smokeCommand = null
        )
        val updated = store.update(
            created.copy(
                state = AgentQueueState.LEASED,
                sourceEvidence = "97cff09c0f362337 [S0013] lines 46-48",
                impactedSymbols = listOf(
                    "src/main/kotlin/atropos/core/agent/AgentRunService.kt:AgentRunService",
                    "src/main/kotlin/atropos/core/agent/AgentJobStore.kt:AgentJobStore"
                )
            ),
            previousState = created.state,
            message = "persist source and impacted evidence"
        )

        val reopened = store.resolve(updated.id) ?: error("missing queue record")
        assertEquals(updated.id, reopened.id)
        assertEquals("97cff09c0f362337 [S0013] lines 46-48", reopened.sourceEvidence)
        assertEquals(
            listOf(
                "src/main/kotlin/atropos/core/agent/AgentRunService.kt:AgentRunService",
                "src/main/kotlin/atropos/core/agent/AgentJobStore.kt:AgentJobStore"
            ),
            reopened.impactedSymbols
        )
        assertTrue(reopened.renderRaw().contains("source evidence: 97cff09c0f362337 [S0013] lines 46-48"))
        assertTrue(reopened.renderRaw().contains("impacted symbols: src/main/kotlin/atropos/core/agent/AgentRunService.kt:AgentRunService"))
    }

    @Test
    fun latest_and_listEntries_prefer_most_recently_updated_queue_entry() {
        val repoRoot = Files.createTempDirectory("atropos-agent-queue-order-")
        val base = Instant.parse("2026-07-27T08:45:00Z")
        var tick = 0L
        val store = AgentQueueStore(repoRoot, clock = { base.plusSeconds(tick++) })

        val older = store.createEntry(task = "older queue entry", smokeCommand = null)
        val newer = store.createEntry(task = "newer queue entry", smokeCommand = null)
        val warmedOlder = store.update(
            older.copy(state = AgentQueueState.LEASED),
            previousState = older.state,
            message = "older entry updated later"
        )

        val listed = store.listEntries(2)
        assertEquals(listOf(warmedOlder.id, newer.id), listed.map { it.id })
        assertEquals(warmedOlder.id, store.latest()?.id)
        assertEquals(warmedOlder.id, store.resolve("latest")?.id)
    }

    @Test
    fun raw_queue_rendering_keeps_lease_state_without_token_identifiers() {
        val repoRoot = Files.createTempDirectory("atropos-agent-queue-lease-render-")
        val store = AgentQueueStore(repoRoot)
        val queued = store.createEntry(task = "render lease state", smokeCommand = null)
        val leased = assertNotNull(store.acquireLease(queued.id, "queue-worker", 60).record)
        val token = assertNotNull(leased.lease).token

        val rendered = leased.renderRaw()

        assertTrue(rendered.contains("lease owner: queue-worker"), rendered)
        assertTrue(rendered.contains("lease acquired at:"), rendered)
        assertTrue(rendered.contains("lease heartbeat at:"), rendered)
        assertTrue(rendered.contains("lease expires at:"), rendered)
        assertFalse(rendered.contains(token), rendered)
        assertFalse(rendered.contains("lease token fingerprint:"), rendered)
    }
}
