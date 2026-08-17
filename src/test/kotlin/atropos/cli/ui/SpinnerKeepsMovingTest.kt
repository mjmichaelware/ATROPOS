/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The thinking indicator has to keep moving.
 *
 * `scheduleAtFixedRate` cancels its schedule permanently and silently the
 * first time the task throws. One exception anywhere under the render — a
 * layout arithmetic slip, a resize mid-frame — and the spinner drew exactly
 * one frame and then sat there for the rest of the session. It looked alive
 * and it was not, with no error to explain it.
 */
class SpinnerKeepsMovingTest {

    @Test
    fun frames_advance_over_time() {
        val frames = ConcurrentLinkedQueue<String>()
        val seen = CountDownLatch(4)

        SpinnerEngine { frame ->
            frame?.let {
                frames += it
                seen.countDown()
            }
        }.use { spinner ->
            spinner.start("Thinking")
            assertTrue(seen.await(3, TimeUnit.SECONDS), "the spinner produced fewer than four frames")
        }

        assertTrue(frames.toSet().size > 1, "every frame was identical: ${frames.toSet()}")
    }

    @Test
    fun a_render_that_throws_does_not_end_the_animation() {
        // The actual defect. Before the guard, the first throw silently
        // cancelled the schedule and nothing ever drew again.
        val delivered = ConcurrentLinkedQueue<String>()
        val attempts = CountDownLatch(5)

        SpinnerEngine { frame ->
            if (frame != null) {
                attempts.countDown()
                if (delivered.size < 2) {
                    delivered += frame
                    throw IllegalStateException("frame could not be drawn")
                }
                delivered += frame
            }
        }.use { spinner ->
            spinner.start("Thinking")
            assertTrue(
                attempts.await(3, TimeUnit.SECONDS),
                "the schedule died after the first failing frame; only ${delivered.size} drawn"
            )
        }

        assertTrue(delivered.size > 2, "rendering never recovered: ${delivered.size} frames")
    }

    @Test
    fun stopping_clears_the_indicator() {
        val cleared = CountDownLatch(1)

        SpinnerEngine { frame -> if (frame == null) cleared.countDown() }.use { spinner ->
            spinner.start("Thinking")
            spinner.stop()
        }

        assertTrue(cleared.await(2, TimeUnit.SECONDS), "stopping left the indicator on screen")
    }

    @Test
    fun stopping_from_another_thread_does_not_hang() {
        // `stop` used to call back into the terminal engine while holding its
        // own monitor, giving two threads two locks in opposite orders. A hung
        // UI is indistinguishable from a slow provider.
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val spinner = SpinnerEngine { frame ->
            if (frame != null) {
                entered.countDown()
                release.await(2, TimeUnit.SECONDS)
            }
        }

        spinner.start("Thinking")
        assertTrue(entered.await(2, TimeUnit.SECONDS), "the spinner never rendered")

        val stopper = Thread { spinner.stop() }.apply { start() }
        stopper.join(2_000)
        release.countDown()

        assertTrue(!stopper.isAlive, "stop() blocked behind an in-flight render")
        spinner.close()
    }
}
