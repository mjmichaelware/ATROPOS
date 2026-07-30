package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue

class AnsiTerminalEngineHelpTest {
    private fun engine(out: ByteArrayOutputStream = ByteArrayOutputStream()): AnsiTerminalEngine =
        AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            out = PrintStream(out),
            errors = PrintStream(ByteArrayOutputStream())
        )

    @Test
    fun renderHelp_groups_commands_and_keeps_navigation_guidance_visible() {
        val out = ByteArrayOutputStream()
        val engine = engine(out)

        engine.renderHelp()

        val rendered = out.toString()

        assertTrue(rendered.contains("COMMANDS"), rendered)
        assertTrue(rendered.contains("group /agent"), rendered)
        assertTrue(rendered.contains("group /self-host"), rendered)
        assertTrue(rendered.contains("group /status"), rendered)
        assertTrue(
            rendered.indexOf("group /agent") < rendered.indexOf("group /self-host"),
            rendered
        )
        assertTrue(
            rendered.indexOf("group /self-host") < rendered.indexOf("group /status"),
            rendered
        )
        assertTrue(rendered.contains("? | /help | /usage | /self-host"), rendered)
    }

    @Test
    fun renderHelp_filters_results_without_triggering_provider_side_effects() {
        val out = ByteArrayOutputStream()
        val engine = engine(out)

        engine.renderHelp("status")

        val rendered = out.toString()

        assertTrue(rendered.contains("filter: status"), rendered)
        assertTrue(rendered.contains("group /agent"), rendered)
        assertTrue(rendered.contains("group /self-host"), rendered)
        assertTrue(rendered.contains("group /status"), rendered)
        assertTrue(rendered.contains("/status quota"), rendered)
        assertTrue(rendered.contains("? | /help | /usage | /self-host"), rendered)
    }
}
