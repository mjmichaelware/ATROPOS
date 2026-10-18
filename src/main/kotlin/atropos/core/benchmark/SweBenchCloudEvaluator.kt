/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.benchmark

import java.io.File
import java.nio.file.Path
import atropos.core.policy.BoundedProcessRunner

/**
 * ATOM-BMK-016: SWE-bench Verified sb-cli Cloud Evaluator.
 *
 * Submits patches directly to Princeton sb-cli cloud evaluation harness,
 * bypassing local 14GB GitHub runner disk limits.
 * Mathematical Invariant: LocalDockerDiskUsage(Evaluator) = 0 bytes.
 */
class SweBenchCloudEvaluator(
    private val runner: BoundedProcessRunner = BoundedProcessRunner()
) {
    fun submitPredictions(predictionsJsonl: File, runId: String, token: String): Int {
        require(predictionsJsonl.exists()) { "Predictions JSONL file missing: ${predictionsJsonl.absolutePath}" }
        require(token.isNotBlank()) { "Cloud evaluation token must not be blank" }

        val command = listOf(
            "sb-cli",
            "submit",
            "swe-bench_verified",
            "test",
            "--predictions_path",
            predictionsJsonl.absolutePath,
            "--run_id",
            runId,
            "--token",
            token
        )

        val workingDir = predictionsJsonl.parentFile?.toPath() ?: Path.of(".")
        val result = runner.run(command, workingDir)
        return result.exitCode ?: 0
    }

    fun prepareRunnerDisk(targetScript: Path = Path.of("scripts/clean-runner-disk.sh")): Path {
        return RunnerDiskOptimizer.writeCleansingScript(targetScript)
    }
}
