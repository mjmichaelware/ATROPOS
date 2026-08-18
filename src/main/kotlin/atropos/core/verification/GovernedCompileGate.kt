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
    /** What this gate runs, readable so a caller can say where it will run. */
    val command: List<String> = listOf("./gradlew", "compileKotlin"),
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

    data class CompileRun(val exitCode: Int, val output: String)

    companion object {
        private val EXIT_PATTERN = Regex("""exit=(-?\d+)""")

        /** The environment variable that moves the compile off this machine. */
        const val REMOTE_FLAG = "ATROPOS_COMPILE_GATE"

        /**
         * The gate, wired to whichever machine can actually compile.
         *
         * `ATROPOS_COMPILE_GATE=github` runs `./gradlew compileKotlin` on
         * GitHub Actions instead of here. That is not a preference: on the
         * Android/Termux install there is no JDK toolchain, `./gradlew` exits
         * 127, and a self-host run stalls one step short of promotion forever
         * with `missing=CANDIDATE_BUILD,JAR_SWAP`. Anything else -- unset
         * included -- keeps the local behaviour, so no existing install
         * changes.
         *
         * Both routes end in the same command and the same
         * [GovernedCompileGateResult]. There is one compile gate; this only
         * decides where its process runs.
         */
        fun forRepository(
            repoRoot: Path,
            environment: Map<String, String> = System.getenv()
        ): GovernedCompileGate {
            val remote = environment[REMOTE_FLAG]?.trim()?.lowercase()
            if (remote != "github" && remote != "ci" && remote != "actions") {
                return GovernedCompileGate(repoRoot)
            }
            return GovernedCompileGate(
                repoRoot = repoRoot,
                // Named for what ran, so the policy record and the evidence
                // line say GitHub Actions rather than claiming a local gradle
                // invocation that never happened.
                command = listOf("github-actions", GitHubActionsCompileRunner.DEFAULT_WORKFLOW, "compileKotlin"),
                processRunner = GitHubActionsCompileRunner(repoRoot)
            )
        }
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
