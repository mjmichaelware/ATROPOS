/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `/ps` answers beside the work, not behind it.
 *
 * The command queue exists to sequence *writes* — commands mutate a shared
 * repository, DAG and goal store. A question is a read, and making an operator
 * wait ten minutes behind a self-host run to ask "what did that error mean" is
 * queue discipline applied where it buys nothing.
 */
class SideConversationServiceTest {

    private fun engine(out: ByteArrayOutputStream) = AnsiTerminalEngine(
        capabilities = ConfigurationManager(envProvider = { null }, hasConsole = false),
        plainOutput = PlainTerminalOutput(out = PrintStream(out), errors = PrintStream(out))
    )

    private fun service(out: ByteArrayOutputStream, busy: String? = null) =
        SideConversationService(
            uiEngine = engine(out),
            cascade = atropos.core.ProviderCascadeRouter(
                atropos.core.ProviderFactory(atropos.core.AtroposConfig.load())
            ),
            activeProvider = { "groq" },
            busyProvider = { busy }
        )

    @Test
    fun an_empty_question_is_refused_with_the_usage() {
        val out = ByteArrayOutputStream()
        service(out).use { it.ask("   ") }

        assertTrue(out.toString().contains("usage: /ps"), "no usage was offered:\n$out")
    }

    @Test
    fun asking_returns_immediately_rather_than_waiting_for_the_provider() {
        // The whole point. If this blocked, the side channel would be just
        // another thing in the queue.
        val out = ByteArrayOutputStream()
        service(out).use { side ->
            val startedAt = System.nanoTime()
            side.ask("what did that error mean")
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertTrue(
                elapsedMillis < 500,
                "ask() blocked for ${elapsedMillis}ms — the operator is waiting again"
            )
        }
    }

    @Test
    fun it_says_when_it_is_routing_around_a_busy_provider() {
        val out = ByteArrayOutputStream()
        service(out, busy = "groq").use { it.ask("quick question") }

        assertTrue(
            out.toString().contains("busy"),
            "the operator is not told why a different provider answered:\n$out"
        )
    }

    @Test
    fun a_second_question_while_one_is_in_flight_is_refused() {
        // Not queued: a side channel that can be spammed into a dozen
        // concurrent provider calls burns quota on answers nobody is reading.
        val out = ByteArrayOutputStream()
        service(out).use { side ->
            side.ask("first")
            side.ask("second")
        }

        assertTrue(
            out.toString().contains("already in flight") || out.toString().contains("one at a time"),
            "a second side question was accepted silently:\n$out"
        )
    }
}
