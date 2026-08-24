/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The counter a one-shot `atropos <command>` exits on.
 *
 * Without it every one-shot exits 0, which makes the binary unusable in a
 * script or a CI step: `atropos auth verify && deploy` would deploy on a
 * tampered authority document.
 */
class TerminalErrorCountTest {

    private fun engine() = AnsiTerminalEngine(
        ConfigurationManager(envProvider = { null }, hasConsole = false)
    )

    @Test
    fun a_clean_run_counts_nothing() {
        val engine = engine()

        engine.renderNotice("all good")

        assertEquals(0, engine.errorCount)
    }

    @Test
    fun every_error_is_counted() {
        val engine = engine()

        engine.renderError("first")
        engine.renderError("second")

        assertEquals(2, engine.errorCount)
    }

    @Test
    fun boot_noise_can_be_cleared_before_the_command_runs() {
        // The authority gate prints its refusal during boot. Counting that
        // would make `atropos auth accept AGENTS.md` exit non-zero on the very
        // run that fixed the thing it was complaining about.
        val engine = engine()
        engine.renderError("AGENTS.md changed since it was recorded")

        engine.resetErrorCount()
        engine.renderNotice("Recorded the current contents of AGENTS.md as authoritative.")

        assertEquals(0, engine.errorCount)
    }

    @Test
    fun terminal_paint_boundary_redacts_notices_blocks_and_errors() {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val engine = AnsiTerminalEngine(
            ConfigurationManager(envProvider = { null }, hasConsole = false),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(output, true, StandardCharsets.UTF_8),
                errors = PrintStream(errors, true, StandardCharsets.UTF_8)
            )
        )
        val secret = "sk-live-secret-123456789"

        engine.renderNotice("notice $secret")
        engine.renderBlock(listOf("block $secret"))
        engine.renderError("error $secret")

        val painted = output.toString(StandardCharsets.UTF_8) + errors.toString(StandardCharsets.UTF_8)
        assertFalse(painted.contains(secret))
        assertTrue(painted.contains("<redacted:api_key>"))
    }
}
