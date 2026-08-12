/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNodeAction
import atropos.core.verification.GovernedCompileGateResult
import java.nio.file.Path

/**
 * Assembles the Phase 11 run proof from runtime facts only.
 *
 * Decision, no presentation and no execution: it hashes what is on disk, reads
 * the porcelain rows the repo reports, and marks a predicate satisfied only when
 * the evidence for it exists. Rendering belongs to the CLI; running the compile
 * belongs to the compile gate.
 */
class SelfHostRunProofBuilder(
    private val repoRoot: Path,
    private val hasher: SelfHostFileHasher = SelfHostFileHasher(),
    private val repoStatus: AgentRunRepoStatus = AgentRunRepoStatus(repoRoot)
) {
    fun build(
        goalId: String,
        dag: DagDefinition?,
        compileGate: GovernedCompileGateResult?,
        evidenceMarkdownPath: String? = null,
        evidenceJsonPath: String? = null
    ): SelfHostRunProof {
        // Only file-mutating nodes produce mutations. A VERIFY node names an
        // expected output as a precondition it inspects — the cradle's identity
        // probe expects a committed `Main.kt` — and a committed file that nothing
        // touched is correctly absent from `git status`. Counting those as
        // mutations would make a healthy run look unverified.
        val expectedPaths = dag?.nodes
            ?.filter { it.action in MUTATING_ACTIONS }
            ?.flatMap { it.expectedOutputs }
            ?.distinct()
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val statusLines = runCatching { repoStatus.statusLines() }.getOrElse { emptyList() }
        val codesByPath = statusLines.associate { it.path to it.code }

        val mutations = expectedPaths.map { path ->
            val absolute = repoRoot.resolve(path).normalize()
            val sha256 = hasher.sha256(absolute)
            SelfHostMutationProof(
                path = path,
                present = sha256 != null,
                sha256 = sha256,
                gitStatusCode = codesByPath[path]
            )
        }

        val satisfied = mutableListOf<SelfHostRunPredicate>()
        val unmet = mutableListOf<SelfHostRunPredicate>()

        // Reaching this builder at all means the prompt was routed here rather
        // than to generic provider chat.
        satisfied += SelfHostRunPredicate.NL_ROUTED

        // A run that declared no expected outputs mutated nothing. That is an
        // unmet predicate, not an empty pass.
        val mutated = mutations.isNotEmpty() && mutations.all { it.present }
        (if (mutated) satisfied else unmet) += SelfHostRunPredicate.SOURCE_MUTATED

        val visible = mutations.isNotEmpty() && mutations.all { it.visibleToGit() }
        (if (visible) satisfied else unmet) += SelfHostRunPredicate.GIT_STATUS_VISIBLE

        val compiled = compileGate?.passed == true
        (if (compiled) satisfied else unmet) += SelfHostRunPredicate.COMPILE_GATE_PASSED

        return SelfHostRunProof(
            goalId = goalId,
            mutations = mutations,
            compileGate = compileGate,
            gitStatusLines = statusLines.map { it.render() },
            satisfiedPredicates = satisfied,
            unmetPredicates = unmet,
            evidenceMarkdownPath = evidenceMarkdownPath,
            evidenceJsonPath = evidenceJsonPath
        )
    }

    private companion object {
        val MUTATING_ACTIONS = setOf(DagNodeAction.CREATE_FILE, DagNodeAction.EDIT_FILE)
    }
}
