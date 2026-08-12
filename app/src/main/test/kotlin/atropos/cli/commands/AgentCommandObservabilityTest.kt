package atropos.cli.commands

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.agent.GoalContinuationService
import atropos.core.agent.GoalRunStatus
import atropos.core.agent.GoalRunStore
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.observability.RunObserver
import java.io.OutputStream
import java.io.PrintStream
import java.net.ServerSocket
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class AgentCommandObservabilityTest {
    @Test
    fun `agent ask ATROPOS reports concrete runtime and self-host state`() {
        val repoRoot = Files.createTempDirectory("atropos-agent-identity-probe-")
        val base = Instant.parse("2026-07-27T08:50:00Z")
        var tick = 0L
        val ui = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(OutputStream.nullOutputStream()),
                errors = PrintStream(OutputStream.nullOutputStream())
            )
        )
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val continuationService = GoalContinuationService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val selfHost = store.createGoalRun("self-host identity probe", provider = "self-host")
        store.update(
            selfHost.copy(
                status = GoalRunStatus.CONTINUING,
                activePhase = "11",
                currentNodeId = "node-cradle"
            )
        )
        repeat(12) { index ->
            store.createGoalRun("newer generic run $index", provider = "codex")
        }
        val command = AgentCommand(
            ui = ui,
            config = config,
            activeProviderName = { "test_provider" },
            continuationService = continuationService
        )

        val result = command.execute(listOf("/agent", "ask", "ATROPOS"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("ATROPOS runtime state"), text)
        assertTrue(text.contains("Self-host goals: 1"), text)
        assertTrue(text.contains("node=node-cradle"), text)
        assertTrue(!text.contains("Greek Fate"), text)
    }

    @Test
    fun `agent watch latest resolves from journaled runs before goal-run latest`() {
        val repoRoot = Files.createTempDirectory("atropos-agent-watch-latest-")
        val base = Instant.parse("2026-07-27T09:00:00Z")
        var tick = 0L
        val ui = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(OutputStream.nullOutputStream()),
                errors = PrintStream(OutputStream.nullOutputStream())
            )
        )
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(repoRoot.resolve("lakehouse").toString(), repoRoot.resolve("lakehouse/vector_storage.db").toString()),
            RuntimeConfig("groq", 0.2)
        )
        val store = GoalRunStore(repoRoot, clock = { base.plusSeconds(tick++) })
        val continuationService = GoalContinuationService(repoRoot = repoRoot, store = store, clock = { base.plusSeconds(tick++) })
        val journal = EventJournalService(repoRoot = repoRoot, clock = { base.plusSeconds(tick++) })
        val observer = RunObserver(config = config, repoRoot = repoRoot, continuationService = continuationService, journal = journal)

        store.createGoalRun("older goal run", provider = "self-host")
        journal.record(runId = "shg-journal-latest", category = EventCategory.LIFECYCLE, payload = "journaled latest run")

        val command = AgentCommand(
            ui = ui,
            config = config,
            activeProviderName = { "test_provider" },
            continuationService = continuationService,
            journal = journal,
            observer = observer
        )

        val result = command.execute(listOf("/agent", "watch", "latest"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }

        assertTrue(result is AgentCommandOutcome.Completed)
        assertTrue(text.contains("journaled latest run"))
    }

    @Test
    fun `agent observe status reports observer last error when startup fails`() {
        val repoRoot = Files.createTempDirectory("atropos-agent-observe-status-")
        val ui = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(OutputStream.nullOutputStream()),
                errors = PrintStream(OutputStream.nullOutputStream())
            )
        )
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

            val command = AgentCommand(
                ui = ui,
                config = config,
                activeProviderName = { "test_provider" },
                observer = observer
            )

            val result = command.execute(listOf("/agent", "observe", "status"))
            val text = when (result) {
                is AgentCommandOutcome.Completed -> result.text
                is AgentCommandOutcome.Invalid -> result.message
            }

            assertTrue(result is AgentCommandOutcome.Completed)
            assertTrue(text.contains("lastError="))
        }
    }

    @Test
    fun `agent observe open refuses when observer is not running and reports last error`() {
        val repoRoot = Files.createTempDirectory("atropos-agent-observe-open-")
        val ui = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(OutputStream.nullOutputStream()),
                errors = PrintStream(OutputStream.nullOutputStream())
            )
        )
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

            val command = AgentCommand(
                ui = ui,
                config = config,
                activeProviderName = { "test_provider" },
                observer = observer
            )

            val result = command.execute(listOf("/agent", "observe", "open"))
            val text = when (result) {
                is AgentCommandOutcome.Completed -> result.text
                is AgentCommandOutcome.Invalid -> result.message
            }

            assertTrue(result is AgentCommandOutcome.Invalid)
            assertTrue(text.contains("observer not running"))
        }
    }
}
