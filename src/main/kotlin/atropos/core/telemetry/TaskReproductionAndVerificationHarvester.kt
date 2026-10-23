/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

/**
 * SD7-T1-TEL15: Task Reproduction & Regression Verification Harvester.
 *
 * Records generalized task reproduction and differential verification telemetry:
 * - Problem statements & task identifiers
 * - Inverted reproduction tests
 * - Candidate patch diffs (filtered strictly to modified source files)
 * - Pre/post verification exit codes and execution verdicts
 */
class TaskReproductionAndVerificationHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    data class ExecutionTrajectory(
        val taskId: String,
        val repository: String,
        val baselineCommit: String,
        val specificationSnippet: String,
        val candidatePatch: String,
        val preVerificationExitCode: Int,
        val postVerificationExitCode: Int,
        val verified: Boolean
    )

    fun recordVerificationRun(trajectory: ExecutionTrajectory): FrontierSweTelemetryRecord {
        val payload = TestAssertionDiffRecord(
            testName = "reproduction_verification_${trajectory.taskId}",
            assertionType = if (trajectory.verified) "PASS" else "FAIL",
            expectedSnippet = "exitCode=0 (verified)",
            actualSnippet = "pre=${trajectory.preVerificationExitCode}, post=${trajectory.postVerificationExitCode}",
            stackTraceSnippet = trajectory.candidatePatch.take(1500)
        )

        val reward = if (trajectory.verified) 2.0 else -1.0
        val record = FrontierSweTelemetryRecord.create(
            instanceId = trajectory.taskId,
            h0PromptLineage = "task-${trajectory.repository}-${trajectory.baselineCommit.take(8)}",
            payload = payload,
            rewardSignal = reward
        )

        ringBuffer?.offer(record)
        return record
    }
}
