/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.nio.file.Path

/**
 * SD7-T1-TEL15: Benchmark Execution & Reproduction Test Harvester.
 *
 * Records standardized benchmark evaluation telemetry (SWE-bench / SWE-bench Pro):
 * - Problem statements & task IDs
 * - Inverted reproduction tests
 * - Candidate patch diffs (filtered to modified source files only)
 * - Pre/post verification exit codes and execution verdicts
 */
class BenchmarkExecutionAndReproductionTestHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    data class BenchmarkTrajectory(
        val instanceId: String,
        val repo: String,
        val baseCommit: String,
        val problemStatementSnippet: String,
        val candidatePatch: String,
        val preTestExitCode: Int,
        val postTestExitCode: Int,
        val verified: Boolean
    )

    fun recordBenchmarkRun(trajectory: BenchmarkTrajectory): FrontierSweTelemetryRecord {
        val payload = TestAssertionDiffRecord(
            testName = "swe_bench_eval_${trajectory.instanceId}",
            assertionType = if (trajectory.verified) "PASS" else "FAIL",
            expectedSnippet = "exitCode=0 (verified)",
            actualSnippet = "pre=${trajectory.preTestExitCode}, post=${trajectory.postTestExitCode}",
            stackTraceSnippet = trajectory.candidatePatch.take(1500)
        )

        val reward = if (trajectory.verified) 2.0 else -1.0
        val record = FrontierSweTelemetryRecord.create(
            instanceId = trajectory.instanceId,
            h0PromptLineage = "bench-${trajectory.repo}-${trajectory.baseCommit.take(8)}",
            payload = payload,
            rewardSignal = reward
        )

        ringBuffer?.offer(record)
        return record
    }
}
