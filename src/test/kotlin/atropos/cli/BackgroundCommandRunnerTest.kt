/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The composer has to stay typeable while a command runs.
 *
 * `router.handleInput()` was called from the key loop, so the loop was inside
 * a self-host run for as long as that run took and read no keys at all. The
 * composer drew its border and its caret and swallowed everything typed into
 * it. Every other agent CLI keeps reading while it works, and the reason is
 * not cosmetic: the moment you most want to say "stop" is the moment a long
 * run is underway.
 */
class BackgroundCommandRunnerTest {

    private fun engine(): Pair<AnsiTerminalEngine, ByteArrayOutputStream> {
        val out = ByteArrayOutputStream()
        return AnsiTerminalEngine(
            capabilities = ConfigurationManager(envProvider = { null }, hasConsole = false),
            plainOutput = PlainTerminalOutput(out = PrintStream(out), errors = PrintStream(out))
        ) to out
    }

    @Test
    fun submitting_does_not_block_the_caller() {
        val (ui, _) = engine()
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        val dispatch: (String) -> RouterOutcome = { text ->
            if (text == "slow") {
                started.countDown()
                gate.await(3, TimeUnit.SECONDS)
            }
            RouterOutcome.CONTINUE
        }

        BackgroundCommandRunner(dispatch, ui).use { runner ->
            val submittedAt = System.nanoTime()
            runner.submit("slow")
            val elapsedMillis = (System.nanoTime() - submittedAt) / 1_000_000

            assertTrue(started.await(3, TimeUnit.SECONDS), "the command never started")
            assertTrue(
                elapsedMillis < 500,
                "submit blocked the caller for ${elapsedMillis}ms — the key loop would be frozen"
            )
            gate.countDown()
        }
    }

    @Test
    fun a_command_typed_during_a_run_is_queued_and_reported() {
        val (ui, out) = engine()
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        val dispatch: (String) -> RouterOutcome = { text ->
            if (text == "slow") {
                started.countDown()
                gate.await(3, TimeUnit.SECONDS)
            }
            RouterOutcome.CONTINUE
        }

        BackgroundCommandRunner(dispatch, ui).use { runner ->
            runner.submit("slow")
            assertTrue(started.await(3, TimeUnit.SECONDS))

            runner.submit("second")
            // Silence would read as a lost keystroke, which is the old bug
            // wearing a different hat.
            assertTrue(
                out.toString().contains("queued"),
                "a command typed during a run was accepted silently:\n$out"
            )
            gate.countDown()
        }
    }

    @Test
    fun commands_run_one_at_a_time_and_in_order() {
        // They mutate a shared repository, DAG and goal store; two at once
        // would interleave writes every downstream verifier assumes are
        // sequential.
        val (ui, _) = engine()
        val order = ConcurrentLinkedQueue<String>()
        val done = CountDownLatch(3)
        val dispatch: (String) -> RouterOutcome = { text ->
            order += "start:$text"
            Thread.sleep(20)
            order += "end:$text"
            done.countDown()
            RouterOutcome.CONTINUE
        }

        BackgroundCommandRunner(dispatch, ui).use { runner ->
            listOf("one", "two", "three").forEach(runner::submit)
            assertTrue(done.await(5, TimeUnit.SECONDS), "not every command ran")
        }

        assertEquals(
            listOf("start:one", "end:one", "start:two", "end:two", "start:three", "end:three"),
            order.toList()
        )
    }

    @Test
    fun a_command_that_throws_does_not_kill_the_worker() {
        val (ui, out) = engine()
        val done = CountDownLatch(1)
        val dispatch: (String) -> RouterOutcome = { text ->
            if (text == "boom") throw IllegalStateException("exploded")
            done.countDown()
            RouterOutcome.CONTINUE
        }

        BackgroundCommandRunner(dispatch, ui).use { runner ->
            runner.submit("boom")
            runner.submit("after")
            assertTrue(done.await(3, TimeUnit.SECONDS), "the worker died with the failing command")
        }

        assertTrue(out.toString().contains("exploded"), "the failure was swallowed:\n$out")
    }

    @Test
    fun an_exit_outcome_is_reported_back_to_the_loop() {
        val (ui, _) = engine()
        val done = CountDownLatch(1)
        val dispatch: (String) -> RouterOutcome = { done.countDown(); RouterOutcome.EXIT }

        BackgroundCommandRunner(dispatch, ui).use { runner ->
            runner.submit("/exit")
            assertTrue(done.await(3, TimeUnit.SECONDS))
            Thread.sleep(100)
            assertTrue(runner.hasExited(), "the loop would never learn the session ended")
        }
    }

}
