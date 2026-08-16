/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.cli.CommandRouter
import atropos.cli.RouterOutcome
import atropos.cli.config.ConfigurationManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import atropos.core.AtroposConfig
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * Runs a CLI command and captures what the engine printed.
 *
 * The bridge does not reimplement any command. It builds the *same*
 * [CommandRouter] the terminal uses, points its renderer at a buffer instead
 * of a console, and returns the text. Every handler, gate and refusal on the
 * path is the one the CLI runs, which is the only way two surfaces can be
 * trusted to agree about what a command did.
 *
 * ## A router per command, not one shared
 *
 * [CommandRouter] holds session state — the active provider, open tabs, a
 * pending risky-input confirmation. One shared instance would let a command
 * from the browser answer a confirmation the phone was asked for, and would
 * make two clients fight over which provider is selected. A fresh router costs
 * a construction per request and removes that entire class of bug.
 *
 * The consequence is stated rather than hidden: a command needing a follow-up
 * ("reply yes to continue") cannot be continued here, because the router that
 * asked is gone by the time the answer arrives. Such a command returns its
 * prompt as output and changes nothing — which is the safe half of the
 * failure. Confirmable flows belong on the approval surface, which is durable.
 */
internal class BridgeCommandRunner(
    private val config: () -> AtroposConfig = { AtroposConfig.load() },
    private val capabilities: () -> ConfigurationManager = { ConfigurationManager() }
) {

    fun run(command: String): BridgeCommandOutput {
        val buffer = ByteArrayOutputStream()
        val stream = PrintStream(buffer, true, StandardCharsets.UTF_8)

        val engine = AnsiTerminalEngine(
            capabilities = capabilities(),
            // Both streams to one buffer. renderError writes to `errors`, so
            // sending only stdout would return an empty body for exactly the
            // commands that refused — the ones the operator most needs to read.
            plainOutput = PlainTerminalOutput(out = stream, errors = stream)
        )

        val router = CommandRouter(
            config = config(),
            uiEngine = engine,
            sessionTracker = QuotaSessionTracker()
        )

        val outcome = try {
            router.handleInput(command)
        } finally {
            // Flushed before reading: the renderer writes through a PrintStream
            // and an unflushed tail would truncate exactly the last line, which
            // is usually the result.
            stream.flush()
            engine.cleanup()
        }

        return BridgeCommandOutput(
            text = buffer.toString(StandardCharsets.UTF_8).trimEnd(),
            exited = outcome == RouterOutcome.EXIT
        )
    }
}
