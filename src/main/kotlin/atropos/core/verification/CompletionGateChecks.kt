package atropos.core.verification

import atropos.core.dag.DagNode
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import atropos.core.security.SourceSecretClassification
import atropos.core.security.SourceSecretScanner
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class CompletionGateChecks(
    private val repoRoot: Path,
    private val clock: () -> Instant,
    private val processRunner: BoundedProcessRunner,
    private val gitRunner: BoundedGitWorktreeCommandRunner,
    private val redactionFilter: RedactionFilter,
    private val sourceSecretScanner: SourceSecretScanner,
    /**
     * The governed compile owner.
     *
     * Locally, defaulted from this checker's own [processRunner] rather than
     * letting [GovernedCompileGate] build its own. Two runners in one gate
     * means the bounds a caller injected — timeout, output ceiling, and the
     * process seam a test substitutes — silently do not apply to the compile,
     * which is the one command in this gate most likely to hang or flood.
     *
     * With `ATROPOS_COMPILE_GATE=github` it is the CI-backed gate instead.
     * That check used to move only for self-host runs, so an operator who had
     * set the variable because their machine cannot compile still watched
     * `/verify` fail on exit 127 — the same failure, on the surface they were
     * more likely to be looking at. The bounds argument does not apply there:
     * a remote gate runs no local compile to bound, and its own timeout is the
     * one that matters.
     */
    private val compileGate: GovernedCompileGate = GovernedCompileGate
        .forRepository(repoRoot)
        .takeIf { it.command.firstOrNull() == GovernedCompileGate.REMOTE_COMMAND }
        ?: GovernedCompileGate(
            repoRoot = repoRoot,
            processRunner = { command, directory ->
                val result = processRunner.run(
                    command = command,
                    directory = directory,
                    timeoutMillis = COMPILE_TIMEOUT_MILLIS,
                    maxOutputBytes = 256 * 1024,
                    maxOutputLines = 4_000
                )
                GovernedCompileGate.CompileRun(
                    exitCode = result.exitCode ?: 1,
                    output = listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n")
                )
            }
        )
) {
    fun checkBuildMatrix(node: DagNode): GateResult {
        val javaVersion = System.getProperty("java.specification.version") ?: ""
        if (javaVersion !in SUPPORTED_JAVA_VERSIONS) {
            return GateResult(node.id, false, "Build Matrix Lock", "unsupported JDK version: expected one of ${SUPPORTED_JAVA_VERSIONS.joinToString()}, observed $javaVersion", clock())
        }
        val wrapperPath = repoRoot.resolve("gradle/wrapper/gradle-wrapper.properties")
        if (!Files.exists(wrapperPath)) {
            return GateResult(node.id, false, "Build Matrix Lock", "gradle-wrapper.properties missing", clock())
        }
        val wrapperContent = Files.readString(wrapperPath)
        if (!wrapperContent.contains("gradle-9.6.0-bin.zip")) {
            return GateResult(node.id, false, "Build Matrix Lock", "incorrect Gradle version in wrapper", clock())
        }
        val buildGradlePath = repoRoot.resolve("build.gradle.kts")
        if (!Files.exists(buildGradlePath)) {
            return GateResult(node.id, false, "Build Matrix Lock", "build.gradle.kts missing", clock())
        }
        val buildGradleContent = Files.readString(buildGradlePath)
        if (!buildGradleContent.contains("1.9.24")) {
            return GateResult(node.id, false, "Build Matrix Lock", "incorrect Kotlin version in build.gradle.kts", clock())
        }
        return GateResult(node.id, true, "Build Matrix Lock", "JDK $javaVersion, Gradle 9.6.0, Kotlin 1.9.24 matrix pinned and verified", clock())
    }

    fun checkImplementationExists(node: DagNode): GateResult {
        val hasPayload = node.actionPayload?.isNotBlank() == true || node.expectedOutputs.isNotEmpty()
        return GateResult(node.id, hasPayload, "Implementation Exists", if (hasPayload) "payload present" else "no action payload or expected outputs", clock())
    }

    fun checkFocusedTests(node: DagNode): GateResult {
        if (node.actionPayload.isNullOrBlank()) {
            return nothingToInspect(node, FOCUSED_TESTS, "no payload to derive a focused test from")
        }
        val testSelector = "*${node.label.replace(" ", "")}*"
        val result = runVerificationCommand(listOf("./gradlew", "test", "--tests", testSelector))
        val passed = result.exitCode == 0 && !result.timedOut && !result.outputTruncated
        return GateResult(node.id, passed, FOCUSED_TESTS, commandDetail(result, "focused tests"), clock())
    }

    fun checkDeterministicVerification(node: DagNode): GateResult {
        try {
            val verifier = DeterministicVerifier(repoRoot)
            val sourcePaths = if (node.territory.isEmpty()) {
                listOf(repoRoot.resolve("src"))
            } else {
                node.territory.map { repoRoot.resolve(it) }
            }
            val patchText = node.actionPayload?.takeIf(::looksLikeUnifiedDiff)
            val result = verifier.verify(sourcePaths, patchText)
            return GateResult(node.id, result.passed, "Deterministic Verification", if (result.passed) "all checks passed" else result.findings.joinToString("; ") { it.evidence }, clock())
        } catch (e: Exception) {
            return GateResult(node.id, false, "Deterministic Verification", "verifier crashed: ${e.message}", clock())
        }
    }

    /**
     * Delegates to the single governed compile owner.
     *
     * This used to run its own `./gradlew compileKotlin` directly: a tool call
     * no policy could refuse and no evidence recorded. The gate keeps its
     * verdict; the process no longer belongs to it.
     */
    fun checkCompileGate(node: DagNode): GateResult {
        val result = compileGate.verify(node.id)
        return GateResult(node.id, result.passed, "Compile Gate", result.message.take(240), clock())
    }

    fun checkTerritoryAndSecrets(node: DagNode): GateResult {
        val gitDiff = gitRunner.run(GitWorktreeOperation.DIFF_NAME_ONLY, repoRoot).takeIf { it.exitCode == 0 }?.output ?: run {
            return GateResult(node.id, false, TERRITORY_AND_SECRETS, "could not read the working diff through bounded Git inspection", clock())
        }
        val changed = gitDiff.lineSequence().filter { it.isNotBlank() }.toList()
        if (node.territory.isEmpty()) {
            return nothingToInspect(node, TERRITORY_AND_SECRETS, "node declared no territory to check changes against")
        }
        val untracked = gitRunner.run(GitWorktreeOperation.UNTRACKED_PATHS, repoRoot)
        if (untracked.exitCode != 0) {
            return GateResult(node.id, false, TERRITORY_AND_SECRETS, "could not enumerate current candidate files through bounded Git inspection", clock())
        }
        val currentPaths = (changed + untracked.output.lineSequence().toList()).distinct()
        val findings = sourceSecretScanner.scan(repoRoot, currentPaths)
        val blockingFindings = findings.filter { it.classification == SourceSecretClassification.REAL_SECRET || it.classification == SourceSecretClassification.UNKNOWN }
        val territoryOk = changed.all { path -> node.territory.any { path.startsWith(it) } }
        val passed = blockingFindings.isEmpty() && territoryOk
        return GateResult(node.id, passed, TERRITORY_AND_SECRETS, when {
            blockingFindings.isNotEmpty() -> "secret findings: ${blockingFindings.joinToString(", ") { "${it.path}:${it.line} rule=${it.ruleId} class=${it.classification} span=${it.redactedSpan}" }}"
            !territoryOk -> "files outside territory"
            else -> "checks passed"
        }, clock())
    }

    fun checkExpectedOutputs(node: DagNode): GateResult {
        if (node.expectedOutputs.isEmpty()) {
            return nothingToInspect(node, EXPECTED_OUTPUTS, "node declared no expected outputs")
        }
        val allExist = node.expectedOutputs.all { path -> Files.exists(repoRoot.resolve(path)) }
        return GateResult(node.id, allExist, EXPECTED_OUTPUTS, if (allExist) "all outputs exist" else "missing: ${node.expectedOutputs.filter { !Files.exists(repoRoot.resolve(it)) }.joinToString(", ")}", clock())
    }

    fun checkUnresolvedDimensions(node: DagNode): GateResult {
        val hasResult = node.result?.isNotBlank() == true || node.finishedAt != null
        return GateResult(node.id, hasResult, "Unresolved Dimensions", if (hasResult) "node has execution result" else "node appears to be a stub (no result)", clock())
    }

    private fun runVerificationCommand(command: List<String>): VerificationCommandResult = runCatching {
        val result = processRunner.run(command = command, directory = repoRoot, timeoutMillis = 900_000, maxOutputBytes = 256 * 1024, maxOutputLines = 4_000)
        VerificationCommandResult(
            exitCode = result.exitCode ?: 1,
            timedOut = result.timedOut,
            outputTruncated = result.outputTruncated,
            output = redactionFilter.compact(listOf(result.stdout, result.stderr).filter { it.isNotBlank() }.joinToString("\n"), 400),
            launchError = result.launchError
        )
    }.getOrElse { failure ->
        VerificationCommandResult(exitCode = 1, timedOut = false, outputTruncated = false, output = "", launchError = "${failure.javaClass.simpleName}: ${failure.message ?: "verification command failed"}")
    }

    private fun commandDetail(result: VerificationCommandResult, subject: String): String = when {
        result.launchError != null -> "$subject failed to start: ${redactionFilter.compact(result.launchError)}"
        result.timedOut -> "$subject timed out"
        result.exitCode != 0 -> "$subject failed (exit=${result.exitCode}): ${result.output.ifBlank { "no command output" }}"
        result.outputTruncated -> "$subject output exceeded the evidence limit"
        else -> "$subject succeeded"
    }

    private fun looksLikeUnifiedDiff(payload: String): Boolean {
        val trimmed = payload.trimStart()
        return trimmed.startsWith("diff --git ") || trimmed.startsWith("--- ") || trimmed.contains("\n--- ") || trimmed.contains("\ndiff --git ")
    }

    private fun nothingToInspect(node: DagNode, gateName: String, why: String): GateResult =
        if (gateName in node.optionalChecks) {
            GateResult(node.id, true, gateName, "$why (declared optional by the node contract)", clock())
        } else {
            GateResult(node.id, false, gateName, "$why; nothing was verified", clock())
        }

    private data class VerificationCommandResult(val exitCode: Int, val timedOut: Boolean, val outputTruncated: Boolean, val output: String, val launchError: String?)
    private companion object {
        const val COMPILE_TIMEOUT_MILLIS = 900_000L
        val SUPPORTED_JAVA_VERSIONS = setOf("17", "21")
        const val FOCUSED_TESTS = "Focused Tests"
        const val TERRITORY_AND_SECRETS = "Territory & Secrets"
        const val EXPECTED_OUTPUTS = "Expected Outputs"
    }
}
