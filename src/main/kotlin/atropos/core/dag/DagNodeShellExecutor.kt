package atropos.core.dag

import atropos.core.factory.ProjectLanguage
import atropos.core.planning.NodeResult
import atropos.core.policy.ActionActor
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.ShellActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path

class DagNodeShellExecutor(
    private val repoRoot: Path,
    private val store: DagStore,
    private val finisher: DagNodeFinisher,
    private val territoryViolation: (DagNode, List<String>) -> String?,
    private val extractCandidatePaths: (String) -> List<String>,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(ExecutionPolicyEngine(repoRoot)),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val executionDir: Path? = null,
    private val projectLanguage: ProjectLanguage? = null
) {
    fun resolveWorkingDir(node: DagNode): Path {
        if (executionDir != null) return executionDir

        val candidates = node.territory + node.expectedOutputs
        for (candidate in candidates) {
            val normalized = candidate.replace('\\', '/').trim().trimStart('/')
            if (normalized.startsWith("apps/") || normalized.startsWith(".atropos/staging/")) {
                val parts = normalized.split('/')
                if (parts.size >= 2) {
                    val resolved = repoRoot.resolve("${parts[0]}/${parts[1]}").normalize()
                    if (Files.exists(resolved) || Files.exists(resolved.parent)) {
                        return resolved
                    }
                }
            }
        }
        return repoRoot
    }

    fun detectLanguage(node: DagNode, workingDir: Path): ProjectLanguage {
        if (projectLanguage != null) return projectLanguage

        if (workingDir != repoRoot && Files.exists(workingDir)) {
            if (Files.exists(workingDir.resolve("pyproject.toml")) ||
                Files.exists(workingDir.resolve("requirements.txt")) ||
                Files.exists(workingDir.resolve("main.py")) ||
                Files.exists(workingDir.resolve("tests"))
            ) {
                return ProjectLanguage.PYTHON
            }
            if (Files.exists(workingDir.resolve("package.json")) ||
                Files.exists(workingDir.resolve("tsconfig.json"))
            ) {
                return ProjectLanguage.TYPESCRIPT
            }
            if (Files.exists(workingDir.resolve("go.mod"))) {
                return ProjectLanguage.GO
            }
            if (Files.exists(workingDir.resolve("Cargo.toml"))) {
                return ProjectLanguage.RUST
            }
            if (Files.exists(workingDir.resolve("pom.xml"))) {
                return ProjectLanguage.JAVA
            }
        }

        val text = (node.territory + node.expectedOutputs + listOf(node.label, node.actionPayload.orEmpty())).joinToString(" ")
        return when (val d = ProjectLanguage.detect(text)) {
            is ProjectLanguage.Detection.Scaffolded -> d.language
            else -> ProjectLanguage.KOTLIN
        }
    }

    fun resolveBuildOrTestCommand(node: DagNode, isBuild: Boolean): String {
        val payload = node.actionPayload?.trim()
        if (!payload.isNullOrBlank() &&
            payload != "verify" &&
            payload != "./gradlew test" &&
            payload != "./gradlew compileKotlin"
        ) {
            return payload
        }

        val workingDir = resolveWorkingDir(node)
        val lang = detectLanguage(node, workingDir)

        return if (isBuild) {
            when (lang) {
                ProjectLanguage.PYTHON -> "python3 -m py_compile \$(find . -name '*.py' ! -path '*/.*') 2>/dev/null || true"
                ProjectLanguage.TYPESCRIPT -> "npm run build 2>/dev/null || npx tsc --noEmit 2>/dev/null || true"
                ProjectLanguage.GO -> "go build ./..."
                ProjectLanguage.RUST -> "cargo check"
                ProjectLanguage.JAVA -> "mvn compile"
                ProjectLanguage.KOTLIN -> "./gradlew compileKotlin"
                else -> "echo build"
            }
        } else {
            when (lang) {
                ProjectLanguage.PYTHON -> "pytest 2>/dev/null || python3 -m unittest discover -s . -p '*test*.py' 2>/dev/null || python3 -m unittest"
                ProjectLanguage.TYPESCRIPT -> "npm test"
                ProjectLanguage.GO -> "go test ./..."
                ProjectLanguage.RUST -> "cargo test"
                ProjectLanguage.JAVA -> "mvn test"
                ProjectLanguage.KOTLIN -> "./gradlew test"
                else -> "sh verify.sh 2>/dev/null || echo test"
            }
        }
    }

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
            resolveBuildOrTestCommand(original, isBuild = false),
            successMessage = null,
            failureMessage = null,
            crashMessage = "build/test failed"
        )

    fun verify(node: DagNode, original: DagNode): DagNodeExecutionResult =
        execute(
            node,
            original,
            resolveBuildOrTestCommand(original, isBuild = false),
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
            resolveBuildOrTestCommand(original, isBuild = true),
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
            val workingDir = resolveWorkingDir(original)

            // STRICT SANDBOXING INVARIANT:
            // Under no circumstances should DagNodeShellExecutor run build or test commands in the ATROPOS root.
            val isBuildOrTest = node.action in setOf(
                DagNodeAction.RUN_BUILD,
                DagNodeAction.RUN_TEST,
                DagNodeAction.VERIFY,
                DagNodeAction.COMPILE_GATE,
                DagNodeAction.SMOKE_GATE,
                DagNodeAction.ACCEPTANCE_GATE
            ) || command.contains("./gradlew") || command.contains("pytest") || command.contains("npm test")

            if (isBuildOrTest && workingDir.normalize() == repoRoot.normalize()) {
                return finisher.fail(
                    running,
                    original,
                    "Containment breach blocked: DagNodeShellExecutor refused to run build/test in ATROPOS root"
                )
            }

            val policyCommand = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (policyCommand.isEmpty()) {
                return finisher.fail(running, original, "DAG command refused: empty command")
            }
            val proposal = ShellActionProposals.forCommand(
                command = policyCommand,
                cwd = workingDir,
                actor = ActionActor.SystemService("dag-execution")
            ).copy(targetPaths = relatedPaths + original.expectedOutputs)
            val authorization = agencyGate.evaluate(proposal)
            if (authorization.disposition != AgencyDisposition.ALLOWED) {
                return finisher.fail(running, original, "DAG command refused by policy: ${authorization.reason}")
            }

            val bounded = processRunner.run(
                command = listOf("sh", "-c", command),
                directory = workingDir,
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
