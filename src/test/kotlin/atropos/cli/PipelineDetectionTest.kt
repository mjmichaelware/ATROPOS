/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * A pasted document is not a shell pipeline.
 *
 * Any un-slashed input containing a single `|` used to be routed to the shell
 * pipeline handler. Markdown tables are built from bars, and so is a line like
 * `auth|list|get|mutate`, so pasting a specification produced "pipeline command
 * contains shell syntax" and nothing else — the document was refused for
 * containing punctuation, with no way to tell from the message what had
 * happened.
 */
class PipelineDetectionTest {

    private fun outputOf(input: String): String {
        val out = ByteArrayOutputStream()
        val engine = AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(out = PrintStream(out), errors = PrintStream(out))
        )
        runCatching {
            CommandRouter(
                config = atropos.core.AtroposConfig.load(),
                uiEngine = engine,
                sessionTracker = atropos.cli.session.QuotaSessionTracker()
            ).handleInput(input)
        }
        engine.cleanup()
        return out.toString()
    }

    @Test
    fun a_pasted_table_row_is_not_a_pipeline() {
        val pasted = "IDs: B-MCP-<SYS>-auth|list|get|mutate|reg|terr|sec estLOC 10-40 each"

        assertFalse(
            outputOf(pasted).contains("pipeline command contains shell syntax"),
            "a document line with bars was refused as a shell pipeline"
        )
    }

    @Test
    fun a_multi_line_paste_is_never_a_pipeline() {
        // A shell pipeline is one line. A document is many, and the document is
        // the thing this CLI exists to accept.
        val pasted = """
            | Item | Owner |
            | --- | --- |
            | Login page | Ana |
        """.trimIndent()

        assertFalse(outputOf(pasted).contains("pipeline command contains shell syntax"))
    }

    @Test
    fun prose_containing_a_bar_is_left_alone() {
        val prose = "Split the work into auth | load | search and report back to me."

        assertFalse(outputOf(prose).contains("pipeline command contains shell syntax"))
    }
}
