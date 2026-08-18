/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.nio.file.Path
import java.time.Instant

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A goal that stopped to ask for something is a goal you can come back to.
 *
 * `EXTERNAL_INPUT_REQUIRED` sets a terminal condition, so `isTerminal()` was
 * true and `/self-host resume` answered "no unfinished self-host goals" for a
 * run that had stopped specifically to ask the operator a question. The
 * durable goal id was recorded, reported on screen, and unreachable the moment
 * the session ended — which is the one thing a durable id exists to prevent.
 */
class ResumableGoalTest {

    private fun run(status: GoalRunStatus, terminal: GoalTerminalCondition? = null) = GoalRunRecord(
        id = "shg-test",
        task = "implement the source document",
        provider = "self-host",
        status = status,
        terminalCondition = terminal,
        continuationCount = 0,
        maxContinuations = 25,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        metaFile = Path.of("goal.json")
    )

    @Test
    fun a_goal_waiting_on_the_operator_is_resumable() {
        val blocked = run(GoalRunStatus.BLOCKED, GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED)

        assertTrue(blocked.isTerminal(), "the autonomous loop must still treat it as terminal")
        assertTrue(blocked.isResumable(), "the operator cannot pick it back up")
    }

    @Test
    fun a_goal_left_by_a_crash_is_resumable() {
        assertTrue(run(GoalRunStatus.RECOVERY_REQUIRED).isResumable())
        assertTrue(run(GoalRunStatus.RUNNING).isResumable())
    }

    @Test
    fun a_finished_goal_is_not_offered_again() {
        assertFalse(run(GoalRunStatus.COMPLETED, GoalTerminalCondition.VERIFIED_COMPLETE).isResumable())
        assertFalse(run(GoalRunStatus.CANCELLED).isResumable())
    }

    @Test
    fun waiting_on_input_never_makes_the_autonomous_loop_spin() {
        // The reason isResumable is not simply the inverse of isTerminal: an
        // unattended runner must not loop on work that is waiting for a human.
        assertFalse(
            run(GoalRunStatus.BLOCKED, GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED).canContinue(),
            "the autonomous loop would spin on a goal waiting for a person"
        )
    }
}
