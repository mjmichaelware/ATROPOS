package atropos.core.journal

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventJournalServiceTest {
    @Test
    fun listRunIds_includes_self_host_runs_and_excludes_non_journal_directories() {
        val repoRoot = Files.createTempDirectory("atropos-journal-runs-")
        val journal = EventJournalService(repoRoot = repoRoot)

        journal.record(runId = "goal-123456789abc", category = EventCategory.LIFECYCLE, payload = "goal run")
        journal.record(runId = "shg-123456789abc", category = EventCategory.LIFECYCLE, payload = "self-host run")
        Files.createDirectories(repoRoot.resolve(".atropos/runs/no-journal-dir"))

        val runIds = journal.listRunIds()

        assertEquals(listOf("goal-123456789abc", "shg-123456789abc"), runIds)
    }

    @Test
    fun readEvents_offset_survives_service_restart_with_monotonic_sequence() {
        val repoRoot = Files.createTempDirectory("atropos-journal-offset-")
        val runId = "shg-offset123456"
        val firstService = EventJournalService(repoRoot = repoRoot)

        val first = firstService.record(runId = runId, category = EventCategory.LIFECYCLE, payload = "started")
        val second = firstService.record(runId = runId, category = EventCategory.LIFECYCLE, payload = "continued")

        val restartedService = EventJournalService(repoRoot = repoRoot)
        val third = restartedService.record(runId = runId, category = EventCategory.LIFECYCLE, payload = "resumed after restart")
        val resumedEvents = restartedService.readEvents(runId = runId, limit = 10, offset = second.sequence)

        assertEquals(1L, first.sequence)
        assertEquals(2L, second.sequence)
        assertEquals(3L, third.sequence)
        assertEquals(listOf(3L), resumedEvents.map { it.sequence })
        assertTrue(resumedEvents.single().payload.contains("resumed after restart"))
    }
}
