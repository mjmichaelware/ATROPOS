/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Priority #8 — evidence-backed evaluation.
 *
 * `VERIFIED_COMPLETE` asserts the work was proven done. A run that gathered
 * nothing has proven nothing, and used to be able to claim it anyway.
 */
class GoalEvidenceGateTest {

    private class Fixture(val root: Path) {
        val store = GoalRunStore(root)
        val service = GoalContinuationService(root)
    }

    private fun fixture() = Fixture(Files.createTempDirectory("atropos-goal-evidence-"))

    @Test
    fun a_run_with_no_evidence_cannot_be_marked_verified_complete() {
        val f = fixture()
        val run = f.service.startRun("prove something")

        val result = f.service.completeRun(run.id, GoalTerminalCondition.VERIFIED_COMPLETE)

        assertTrue(!result.ok, "verified completion without evidence must be refused")
        assertTrue(result.message.contains("no evidence"), result.message)

        // And the run must not have been quietly marked complete anyway.
        assertTrue(f.store.resolve(run.id)?.status != GoalRunStatus.COMPLETED)
    }

    @Test
    fun a_run_that_recorded_evidence_can_be_marked_verified_complete() {
        val f = fixture()
        val run = f.service.startRun("prove something")
        f.store.update(run.copy(evidence = listOf("compile: BUILD SUCCESSFUL")))

        val result = f.service.completeRun(run.id, GoalTerminalCondition.VERIFIED_COMPLETE)

        assertTrue(result.ok, "evidence-backed completion must still be allowed: ${result.message}")
        assertEquals(GoalRunStatus.COMPLETED, result.record?.status)
    }

    @Test
    fun the_evidence_gate_applies_only_to_verified_completion() {
        // Blocked, cancelled and failed are statements about *not* completing;
        // requiring proof of work for them would be nonsense.
        listOf(
            GoalTerminalCondition.POLICY_BLOCKED,
            GoalTerminalCondition.CANCELLED,
            GoalTerminalCondition.TERMINAL_FAILURE,
            GoalTerminalCondition.RETRY_BUDGET_EXHAUSTED,
            GoalTerminalCondition.EXTERNAL_INPUT_REQUIRED
        ).forEach { condition ->
            val f = fixture()
            val run = f.service.startRun("goal for $condition")
            val result = f.service.completeRun(run.id, condition, reason = "test")
            assertTrue(result.ok, "$condition must not require evidence: ${result.message}")
        }
    }
}
