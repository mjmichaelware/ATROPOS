package atropos.core.dag

import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ShellActionProposals
import atropos.core.planning.NodeResult
import atropos.core.security.RedactionFilter
import java.nio.file.Path

class DagNodeShellExecutor(
    private val repoRoot: Path,
    private val store: DagStore,
    private val finisher: DagNodeFinisher,
    private val territoryViolation: (DagNode, List<String>) -> String?,
    private val extractCandidatePaths: (String) -> List<String>,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner()
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
            val policyCommand = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (policyCommand.isEmpty()) {
                return finisher.fail(running, original, "DAG command refused: empty command")
            }
            val proposal = ShellActionProposals.forCommand(
                command = policyCommand,
                cwd = repoRoot,
                actor = ActionActor.SystemService("dag-execution")
            ).copy(targetPaths = relatedPaths + original.expectedOutputs)
            val authorization = agencyGate.evaluate(proposal)
            if (authorization.disposition != AgencyDisposition.ALLOWED) {
                return finisher.fail(running, original, "DAG command refused by policy: ${authorization.reason}")
            }

            val bounded = processRunner.run(
                command = listOf("sh", "-c", command),
                directory = repoRoot,
                timeoutMillis = 900_000L,
                maxOutputBytes = 256 * 1024,
                maxOutputLines = 4_000
            )
            val output = listOf(bounded.stdout, bounded.stderr)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trimEnd()
            val exitCode = bounded.exitCode
            val success = exitCode == 0 && !bounded.timedOut && !bounded.outputTruncated && bounded.launchError == null
            val state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED
            val failureDetail = when {
                bounded.launchError != null -> "launch failed: ${redactionFilter.redact(bounded.launchError)}"
                bounded.timedOut -> "timed out"
                bounded.outputTruncated -> "output exceeded bounds"
                else -> output.take(200)
            }
            val message = if (success) successMessage ?: output.take(200) else failureMessage ?: failureDetail
            finisher.complete(
                running,
                NodeResult(
                    nodeId = original.id,
                    success = success,
                    message = message,
                    finalState = state,
                    result = output.take(2000).ifBlank { message },
                    failureReason = if (success) null else failureReason(
                        exitCode ?: -1,
                        redactionFilter.redact(failureDetail).take(MAX_FAILURE_DETAIL)
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
