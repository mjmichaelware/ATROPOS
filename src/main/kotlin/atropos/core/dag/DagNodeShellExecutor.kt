package atropos.core.dag

import atropos.core.planning.NodeResult
import atropos.core.security.RedactionFilter
import java.nio.file.Path

class DagNodeShellExecutor(
    private val repoRoot: Path,
    private val store: DagStore,
    private val finisher: DagNodeFinisher,
    private val territoryViolation: (DagNode, List<String>) -> String?,
    private val extractCandidatePaths: (String) -> List<String>,
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun runCommand(node: DagNode, original: DagNode): DagNodeExecutionResult {
        val command = original.actionPayload ?: "echo no command specified"
        val territoryFailure = territoryViolation(original, extractCandidatePaths(command) + original.expectedOutputs)
        if (territoryFailure != null) {
            val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
            return finisher.fail(running, original, territoryFailure)
        }
        return execute(
            node,
            original,
            command,
            successMessage = null,
            failureMessage = null,
            relatedPaths = extractCandidatePaths(command),
            crashMessage = "command failed"
        )
    }

    fun buildTest(node: DagNode, original: DagNode): DagNodeExecutionResult =
        execute(
            node,
            original,
            original.actionPayload ?: "./gradlew test",
            successMessage = null,
            failureMessage = null,
            crashMessage = "build/test failed"
        )

    fun verify(node: DagNode, original: DagNode): DagNodeExecutionResult =
        execute(
            node,
            original,
            original.actionPayload ?: "./gradlew test",
            verifying = true,
            successMessage = "verification passed",
            failureMessage = "verification failed",
            failureReason = { _, _ -> "verification failed" },
            crashMessage = "verify failed"
        )

    fun gate(node: DagNode, original: DagNode): DagNodeExecutionResult =
        execute(
            node,
            original,
            original.actionPayload ?: "./gradlew compileKotlin",
            successMessage = "gate passed",
            failureMessage = "gate failed",
            failureReason = { exitCode, _ -> "gate failed: exit $exitCode" },
            crashMessage = "gate crashed"
        )

    private fun execute(
        node: DagNode,
        original: DagNode,
        command: String,
        verifying: Boolean = false,
        successMessage: String?,
        failureMessage: String?,
        relatedPaths: List<String> = emptyList(),
        failureReason: (Int, String) -> String = { exitCode, detail ->
            "exit code $exitCode${detail.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}"
        },
        crashMessage: String
    ): DagNodeExecutionResult {
        val running = store.writeNode(node.copy(state = if (verifying) DagNodeState.VERIFYING else DagNodeState.RUNNING))
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trimEnd()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            val message = if (success) successMessage ?: output.take(200) else failureMessage ?: output.take(200)
            finisher.complete(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = success,
                    message = message,
                    finalState = state,
                    result = output.take(2000).ifBlank { message },
                    failureReason = if (success) null else failureReason(
                        exitCode,
                        redactionFilter.redact(output).take(MAX_FAILURE_DETAIL)
                    )
                ),
                relatedPaths = relatedPaths
            )
            DagNodeExecutionResult(original.id, state, success, message)
        } catch (e: Exception) {
            finisher.fail(running, original, e.message ?: crashMessage)
        }
    }

    private companion object {
        const val MAX_FAILURE_DETAIL = 500
    }
}
