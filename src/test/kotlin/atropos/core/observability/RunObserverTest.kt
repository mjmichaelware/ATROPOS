package atropos.core.observability

import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import java.nio.file.Files
import java.net.ServerSocket
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunObserverTest {
    @Test
    fun listRuns_prefers_journaled_runs_over_goal_run_history() {
        val repoRoot = Files.createTempDirectory("atropos-run-observer-runs-")
        val base = Instant.parse("2026-07-27T09:10:00Z")
        var tick = 0L
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        val journal = EventJournalService(repoRoot = repoRoot, clock = { base.plusSeconds(tick++) })
        journal.record(runId = "goal-older", category = EventCategory.LIFECYCLE, payload = "older")
        journal.record(runId = "shg-newer", category = EventCategory.LIFECYCLE, payload = "newer")

        val observer = RunObserver(config = config, repoRoot = repoRoot, journal = journal)
        val text = observer.listRuns(2)

        assertTrue(text.contains("journaled runs:"))
        assertTrue(text.lines().any { it.contains("shg-newer") })
        assertTrue(text.lines().any { it.contains("goal-older") })
    }

    @Test
    fun status_reports_last_error_after_observer_start_failure() {
        val repoRoot = Files.createTempDirectory("atropos-run-observer-error-")
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        ServerSocket(0).use { occupied ->
            val observer = RunObserver(config = config, repoRoot = repoRoot)
            observer.start(occupied.localPort)

            var state = observer.status()
            repeat(20) {
                if (state.lastError != null || !state.running) return@repeat
                Thread.sleep(25)
                state = observer.status()
            }

            assertFalse(state.running)
            assertNotNull(state.lastError)
        }
    }
}
