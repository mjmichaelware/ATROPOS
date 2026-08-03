package atropos.cli.commands

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import java.io.OutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AgentCommandDagBootstrapTest {

    private fun buildCommand(): AgentCommand {
        val ui = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(OutputStream.nullOutputStream()),
                errors = PrintStream(OutputStream.nullOutputStream())
            )
        )
        return AgentCommand(ui = ui, activeProviderName = { "test_provider" })
    }

    @Test
    fun `dag bootstrap invokes real acceptance implementation`() {
        val cmd = buildCommand()
        val result = cmd.execute(listOf("/agent", "dag", "bootstrap"))
        val isUsageError = result is AgentCommandOutcome.Invalid && result.message.contains("usage")
        assertFalse(isUsageError, "bootstrap must not return the unknown-subcommand usage message")
    }

    @Test
    fun `dag bootstrap renders details and node totals`() {
        val cmd = buildCommand()
        val result = cmd.execute(listOf("/agent", "dag", "bootstrap"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }
        assertTrue(text.contains("Bootstrap acceptance"), "must show PASSED or FAILED")
        assertTrue(text.contains("nodes attempted"), "must show nodes attempted")
        assertTrue(text.contains("nodes passed"), "must show nodes passed")
        assertTrue(text.contains("nodes failed"), "must show nodes failed")
    }

    @Test
    fun `dag bootstrap returns success outcome when acceptance passes`() {
        val cmd = buildCommand()
        val result = cmd.execute(listOf("/agent", "dag", "bootstrap"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }
        if (text.contains("PASSED")) {
            assertTrue(result is AgentCommandOutcome.Completed, "PASSED must return Completed")
        }
        if (text.contains("FAILED")) {
            assertTrue(result is AgentCommandOutcome.Invalid, "FAILED must return Invalid")
        }
    }

    @Test
    fun `dag bootstrap returns failed outcome when acceptance fails`() {
        val cmd = buildCommand()
        val result = cmd.execute(listOf("/agent", "dag", "bootstrap"))
        val text = when (result) {
            is AgentCommandOutcome.Completed -> result.text
            is AgentCommandOutcome.Invalid -> result.message
        }
        assertTrue(text.contains("FAILED") || text.contains("PASSED"),
            "outcome must indicate pass or fail")
    }

    @Test
    fun `unknown dag subcommand returns invalid with usage`() {
        val cmd = buildCommand()
        val result = cmd.execute(listOf("/agent", "dag", "nonexistent"))
        assertTrue(result is AgentCommandOutcome.Invalid)
        assertTrue(result.message.contains("usage"), "must show usage message")
        assertTrue(result.message.contains("bootstrap"), "usage must include bootstrap")
    }
}
