/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.recovery

import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Priority #8 — automatic restart continuity.
 *
 * Crash recovery was real but only ran when an operator asked for it. These pin
 * the supervisor's contract: it runs, it runs once, and it never hides a
 * failure.
 */
class RuntimeContinuitySupervisorTest {

    private fun report(
        queue: Int = 0, sessions: Int = 0, dag: Int = 0, runs: Int = 0,
        errors: List<String> = emptyList()
    ) = RecoveryReport(
        recoveredAt = Instant.parse("2026-07-27T12:00:00Z"),
        staleQueueEntries = queue,
        staleSessions = sessions,
        staleDagClaims = dag,
        interruptedRuns = runs,
        completedMutationsSkipped = 0,
        errors = errors,
        message = "test"
    )

    @Test
    fun recovery_runs_without_anyone_asking_for_it() {
        val calls = AtomicInteger(0)
        val supervisor = RuntimeContinuitySupervisor { calls.incrementAndGet(); report(queue = 2) }

        val outcome = supervisor.ensureRecovered()

        assertEquals(1, calls.get(), "startup must sweep durable state on its own")
        assertTrue(outcome is ContinuityOutcome.Recovered)
        assertTrue(outcome.repairedSomething)
    }

    @Test
    fun the_global_sweep_runs_exactly_once_per_process() {
        val calls = AtomicInteger(0)
        val supervisor = RuntimeContinuitySupervisor { calls.incrementAndGet(); report() }

        supervisor.ensureRecovered()
        val second = supervisor.ensureRecovered()
        val third = supervisor.ensureRecovered()

        assertEquals(1, calls.get(), "re-sweeping would re-examine state this process is changing")
        assertEquals(ContinuityOutcome.AlreadyRecovered, second)
        assertEquals(ContinuityOutcome.AlreadyRecovered, third)
    }

    @Test
    fun a_failed_sweep_is_reported_not_swallowed() {
        val supervisor = RuntimeContinuitySupervisor { error("queue store unreadable") }

        val outcome = supervisor.ensureRecovered()

        assertTrue(outcome is ContinuityOutcome.Failed)
        assertTrue(outcome.reason.contains("queue store unreadable"), outcome.reason)
        assertTrue(
            supervisor.startupNotice(outcome)!!.contains("did not run"),
            "the operator must learn recovery failed rather than assume it worked"
        )
    }

    @Test
    fun a_clean_start_says_nothing_and_a_repair_says_what_it_did() {
        val quiet = RuntimeContinuitySupervisor { report() }
        assertNull(quiet.startupNotice(quiet.ensureRecovered()), "a clean start must not add noise")

        val busy = RuntimeContinuitySupervisor { report(queue = 1, sessions = 2, dag = 3, runs = 4) }
        val notice = busy.startupNotice(busy.ensureRecovered())!!
        assertTrue(notice.contains("1 queue"), notice)
        assertTrue(notice.contains("2 session"), notice)
        assertTrue(notice.contains("3 dag claim"), notice)
        assertTrue(notice.contains("4 interrupted run"), notice)
    }

    @Test
    fun errors_during_a_partial_sweep_still_surface() {
        val supervisor = RuntimeContinuitySupervisor {
            report(queue = 1, errors = listOf("session recovery: permission denied"))
        }

        val outcome = supervisor.ensureRecovered()
        val notice = supervisor.startupNotice(outcome)!!

        assertTrue(notice.contains("permission denied"), notice)
        assertTrue(!outcome.safeForSelfHostContinuation, "self-host must not continue after partial recovery")
    }

    @Test
    fun a_sweep_that_found_nothing_is_not_treated_as_a_repair() {
        val supervisor = RuntimeContinuitySupervisor { report() }
        val outcome = supervisor.ensureRecovered()

        assertTrue(outcome is ContinuityOutcome.Recovered)
        assertTrue(!outcome.repairedSomething, "an empty sweep repaired nothing")
    }
}
