/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.interrupt

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `SUP.UX.INTERRUPT-PRIMITIVE`: "freeze → resume restores exact DAG position
 * and evidence". The position has to survive the process, because the reason
 * to freeze a long phone job is usually that the process is about to end.
 */
class FrozenRunStoreTest {

    private fun store() =
        FrozenRunStore(Files.createTempDirectory("atropos-freeze-test").resolve("frozen.tsv"))

    @Test
    fun `a frozen position round-trips with its evidence`() {
        val store = store()
        val record = FrozenRun(
            runId = "run-42",
            resumePoint = "dag:node-7:verify",
            level = InterruptLevel.FREEZE,
            frozenAt = Instant.parse("2026-08-12T10:15:30Z"),
            evidencePaths = listOf(".atropos/evidence/bundle-a", ".atropos/evidence/bundle-b")
        )

        assertTrue(store.freeze(record))

        assertEquals(record, store.read())
    }

    @Test
    fun `nothing frozen reads as null rather than an empty record`() {
        assertNull(store().read())
    }

    @Test
    fun `a truncated record is rejected rather than half-believed`() {
        val store = store()
        store.freeze(
            FrozenRun("run-1", "node-1", InterruptLevel.FREEZE, Instant.now())
        )

        assertNull(FrozenRun.decode("runId\trun-1\n"))
    }

    @Test
    fun `clearing removes the record`() {
        val store = store()
        store.freeze(FrozenRun("run-1", "node-1", InterruptLevel.FREEZE, Instant.now()))

        assertTrue(store.clear())

        assertNull(store.read())
    }

    @Test
    fun `a freeze without a resume point is refused and the run keeps running`() {
        val dir = Files.createTempDirectory("atropos-freeze-registry")
        InterruptRegistry.useStore(dir.resolve("frozen.tsv"))
        InterruptRegistry.controller.clear()
        InterruptRegistry.request(InterruptLevel.FREEZE, "test")

        val outcome = InterruptRegistry.take("run-1", resumePoint = null)

        assertTrue(outcome is InterruptOutcome.Refused)
        assertTrue(outcome.reason.contains("needs a resume point"))
        assertFalse(InterruptRegistry.controller.state().isStopped)
        InterruptRegistry.controller.clear()
    }

    @Test
    fun `a freeze is durable before the interrupt is marked taken`() {
        val dir = Files.createTempDirectory("atropos-freeze-registry")
        InterruptRegistry.useStore(dir.resolve("frozen.tsv"))
        InterruptRegistry.controller.clear()
        InterruptRegistry.request(InterruptLevel.FREEZE, "test")

        val outcome = InterruptRegistry.take("run-9", "dag:node-3")

        assertTrue(outcome is InterruptOutcome.Taken)
        assertEquals("dag:node-3", InterruptRegistry.frozen()?.resumePoint)

        val resumed = InterruptRegistry.resume()
        assertEquals("run-9", resumed?.runId)
        assertNull(InterruptRegistry.frozen())
        InterruptRegistry.controller.clear()
    }

    @Test
    fun `a stronger interrupt supersedes a weaker one and never the reverse`() {
        val controller = InterruptController()
        controller.request(InterruptLevel.SOFT, "operator")
        controller.request(InterruptLevel.HARD, "operator")

        assertEquals(InterruptLevel.HARD, controller.state().requested?.level)

        controller.request(InterruptLevel.SOFT, "operator")
        assertEquals(InterruptLevel.HARD, controller.state().requested?.level)
    }

    @Test
    fun `requested is not stopped until the loop takes it`() {
        val controller = InterruptController()
        controller.request(InterruptLevel.SOFT, "operator")

        assertTrue(controller.state().isPending)
        assertFalse(controller.state().isStopped)
        assertTrue(controller.shouldStop())

        controller.take("node-1")
        assertTrue(controller.state().isStopped)
        assertFalse(controller.shouldStop())
    }
}
