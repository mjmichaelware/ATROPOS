/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Path

/**
 * ATOM CC7: WORKSPACE LIFECYCLE HOOKS EXECUTION RUNNER
 * Path: src/main/kotlin/atropos/core/policy/WorkspaceLifecycleHooksRunner.kt
 *
 * Executes declared lifecycle scripts automatically during workspace lifecycle
 * phases (after repository clone, upon container start, or upon dependency changes).
 * Streams logs directly to the integrated output console with timeout protection.
 */
enum class LifecycleHookPhase {
    POST_CREATE,
    POST_START,
    UPDATE_CONTENT
}

data class LifecycleExecutionSummary(
    val phase: LifecycleHookPhase,
    val command: String,
    val exitCode: Int,
    val output: String,
    val durationMillis: Long,
    val timedOut: Boolean
)

class WorkspaceLifecycleHooksRunner(
    private val workingDirectory: Path = Path.of(System.getProperty("user.dir")),
    private val defaultTimeoutMillis: Long = 60_000L
) {
    fun executeHook(
        phase: LifecycleHookPhase,
        lifecycleScripts: Map<String, String>
    ): LifecycleExecutionSummary? {
        val key = when (phase) {
            LifecycleHookPhase.POST_CREATE -> "postCreate"
            LifecycleHookPhase.POST_START -> "postStart"
            LifecycleHookPhase.UPDATE_CONTENT -> "updateContent"
        }

        val command = lifecycleScripts[key] ?: return null
        val startTime = System.currentTimeMillis()

        return try {
            val process = ProcessBuilder("/bin/sh", "-c", command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(defaultTimeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
            val duration = System.currentTimeMillis() - startTime

            if (!completed) {
                process.destroyForcibly()
                LifecycleExecutionSummary(
                    phase = phase,
                    command = command,
                    exitCode = -1,
                    output = "Command timed out after ${defaultTimeoutMillis}ms",
                    durationMillis = duration,
                    timedOut = true
                )
            } else {
                val outputText = process.inputStream.bufferedReader().readText()
                LifecycleExecutionSummary(
                    phase = phase,
                    command = command,
                    exitCode = process.exitValue(),
                    output = outputText,
                    durationMillis = duration,
                    timedOut = false
                )
            }
        } catch (ex: Exception) {
            LifecycleExecutionSummary(
                phase = phase,
                command = command,
                exitCode = -1,
                output = "Execution failed: ${ex.message}",
                durationMillis = System.currentTimeMillis() - startTime,
                timedOut = false
            )
        }
    }

    companion object {
        val defaultRunner = WorkspaceLifecycleHooksRunner()
    }
}
