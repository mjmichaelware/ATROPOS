/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.policy.BoundedProcessRunner

/**
 * SD7-T1-TEL06: Toolchain Execution Trajectory Harvester.
 *
 * Captures toolchain executions including argv, exit codes, search reformulations,
 * and truncated standard I/O byte streams (4 KB bound).
 */
class ToolchainExecutionTrajectoryHarvester(
    private val ringBuffer: TelemetryRingBuffer? = null,
    private val maxStreamBytes: Int = 4096
) {
    /**
     * Records a toolchain command execution trajectory.
     */
    fun recordExecution(
        instanceId: String,
        argv: List<String>,
        exitCode: Int,
        stdout: String,
        stderr: String,
        durationMs: Long
    ) {
        val truncatedStdout = stdout.take(maxStreamBytes)
        val truncatedStderr = stderr.take(maxStreamBytes)

        val payload = AgenticShellExecution(
            argv = argv,
            exitCode = exitCode,
            stdoutTruncated = truncatedStdout,
            stderrTruncated = truncatedStderr,
            durationMs = durationMs
        )

        val reward = if (exitCode == 0) 0.1 else -0.5
        val record = FrontierSweTelemetryRecord.create(
            instanceId = instanceId,
            h0PromptLineage = "argv-${argv.firstOrNull() ?: "cmd"}",
            payload = payload,
            rewardSignal = reward
        )
        ringBuffer?.offer(record)
    }
}
