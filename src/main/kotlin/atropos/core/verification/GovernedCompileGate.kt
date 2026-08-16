/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.policy.ActionActor
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.BoundedProcessRunner
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.TypedToolExecutor
import atropos.core.policy.VerificationActionProposals
import atropos.core.security.RedactionFilter
import java.nio.file.Path

/**
 * The compile gate for mutated Kotlin sources, run under execution policy.
 *
 * This is the single owner of "did the tree we just mutated still compile?".
 * It exists because a raw JVM process launcher for `./gradlew compileKotlin` inside
 * a verification check is an ungoverned tool call: nothing proposes it, nothing
 * can refuse it, and nothing records that it ran. Every caller — the completion
 * gate and the self-host run chain alike — goes through here so the compile is
 * proposed, policy-decided, bounded, and evidenced.
 *
 * Fail-closed by construction: a refused proposal, an unbound executor, or a
 * process that never started all yield `passed=false` with a null exit code.
 */
class GovernedCompileGate(
    private val repoRoot: Path,
    private val command: List<String> = listOf("./gradlew", "compileKotlin"),
    private val agency: TypedToolExecutor = TypedToolExecutor(BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: (List<String>, Path) -> CompileRun = ::runGovernedCompileProcess
) {
    /**
     * @param actorId identifies who asked for the compile (a DAG node id or a
     * self-host goal id). It travels into the proposal so the policy record
     * names the requester rather than an anonymous build.
     */
    fun verify(actorId: String): GovernedCompileGateResult {
        // A tree with no build cannot be compiled, and reporting that as a
        // compile failure is a false negative — the same defect as a false
        // VERIFIED, pointed the other way. `./gradlew compileKotlin` in a
        // directory with no gradlew exits non-zero because there is nothing to
        // run, not because the source is broken, and a self-host mutation in
        // such a tree was being reverted for it.
        //
        // Narrow on purpose: the engine's own checkout has both files, so the
        // gate still runs everywhere a compile is actually possible.
        if (!hasBuildSystem()) {
            return GovernedCompileGateResult(
                passed = true,
                command = command,
                exitCode = null,
                message = "compile gate not applicable: no build system in ${repoRoot.fileName}",
                proposalId = "compile-gate-not-applicable"
            )
        }
        val proposal = VerificationActionProposals.buildTest(
            command = command,
            repoRoot = repoRoot,
            actor = ActionActor.HierarchyNode(role = "compile-gate", nodeId = actorId)
        )
        val execution = runCatching {
            agency.execute(proposal) {
                val run = processRunner(command, repoRoot)
                "exit=${run.exitCode}\n${redactionFilter.compact(run.output, 2_000)}"
            }
        }.getOrElse { failure ->
            return GovernedCompileGateResult(
                passed = false,
                command = command,
                exitCode = null,
                message = "compile gate failed to start: ${redactionFilter.compact(failure.message.orEmpty(), 240)}",
                proposalId = proposal.id
            )
        }
        if (!execution.authorized || !execution.executed) {
            return GovernedCompileGateResult(
                passed = false,
                command = command,
                exitCode = null,
                message = "compile gate refused: ${execution.refusalReason ?: execution.disposition.name}",
                proposalId = proposal.id,
                refusalReason = execution.refusalReason ?: execution.disposition.name
            )
        }
        val output = execution.output.orEmpty()
        val exitCode = EXIT_PATTERN.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (exitCode == null) {
            return GovernedCompileGateResult(
                passed = false,
                command = command,
                exitCode = null,
                message = "compile gate produced no exit code: ${redactionFilter.compact(output, 240)}",
                proposalId = proposal.id
            )
        }
        if (exitCode != 0) {
            return GovernedCompileGateResult(
                passed = false,
                command = command,
                exitCode = exitCode,
                message = "compile failed: ${redactionFilter.compact(output, 240)}",
                proposalId = proposal.id
            )
        }
        return GovernedCompileGateResult(
            passed = true,
            command = command,
            exitCode = 0,
            message = "compilation succeeded",
            proposalId = proposal.id
        )
    }

    private fun hasBuildSystem(): Boolean =
        java.nio.file.Files.isRegularFile(repoRoot.resolve("gradlew")) ||
            java.nio.file.Files.isRegularFile(repoRoot.resolve("build.gradle.kts")) ||
            java.nio.file.Files.isRegularFile(repoRoot.resolve("build.gradle"))

    data class CompileRun(val exitCode: Int, val output: String)

    private companion object {
        val EXIT_PATTERN = Regex("""exit=(-?\d+)""")
    }
}

private fun runGovernedCompileProcess(
    command: List<String>,
    repoRoot: Path
): GovernedCompileGate.CompileRun {
    val result = BoundedProcessRunner().run(
        command = command,
        directory = repoRoot,
        timeoutMillis = 1_800_000L,
        maxOutputBytes = 256 * 1024,
        maxOutputLines = 4_000
    )
    val output = buildString {
        result.launchError?.let(::appendLine)
        append(result.stdout)
        append(result.stderr)
    }
    return GovernedCompileGate.CompileRun(result.exitCode ?: 127, output)
}
