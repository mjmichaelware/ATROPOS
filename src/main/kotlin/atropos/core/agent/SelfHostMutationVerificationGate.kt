/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.verification.CompletionGateReport
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import java.nio.file.Path

/**
 * Makes a merged self-mutation face the real completion gate before it counts.
 *
 * The hole this closes: [SelfHostWorktreeNodeExecutor] merged a mutation into the
 * live working tree and then wrote `DagNodeState.COMPLETE` directly. The only
 * check between those two steps was `git diff --check` — a whitespace lint. A
 * self-host run could therefore land source that does not compile and record the
 * node as COMPLETE, and the compile gate would not be consulted until promotion,
 * long after the change was already on disk. `C1-SB-02` says a nonzero compile or
 * test exit must forbid VERIFIED; that was true at the promotion boundary and
 * false at the mutation boundary, which is the boundary that actually writes.
 *
 * This owner does not verify anything itself. It asks [VerifiedCompletionGate] —
 * the single verification owner — and then does the part the gate cannot: undo a
 * change that already landed.
 *
 * ## Why rejection has to reverse the patch
 *
 * By the time verification runs, the isolated worktree has been merged and
 * removed, so rolling *it* back reverts nothing. Leaving the failed change in
 * place would be worse than a failed run: the tree would carry uncompilable
 * source that no goal claims, and the next run would inherit it as its baseline.
 * Reversing the same diff in the repository it landed in is the only way back to
 * the state the run started from.
 *
 * A reversal that itself fails is reported rather than swallowed. An operator
 * facing a dirty tree needs to know the tree is dirty; a silent failure here
 * would leave the very state this class exists to prevent, while claiming to
 * have prevented it.
 */
class SelfHostMutationVerificationGate(
    private val repoRoot: Path,
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(repoRoot = repoRoot),
    private val gitRunner: BoundedGitWorktreeCommandRunner = BoundedGitWorktreeCommandRunner(),
    private val evaluate: (DagNode) -> CompletionGateReport = completionGate::evaluateNode
) {

    /**
     * @param mergedDiff the patch that was applied to [repoRoot], needed to undo it.
     */
    fun verifyMerged(node: DagNode, mergedDiff: String): SelfHostMutationVerdict {
        val report = runCatching { evaluate(node) }.getOrElse { failure ->
            // A gate that crashed did not pass. Treating a crash as anything
            // other than refusal is how "verified" stops meaning verified.
            return reject(
                node,
                mergedDiff,
                "completion gate crashed: ${failure.javaClass.simpleName}",
                report = null
            )
        }

        if (report.canComplete) {
            return SelfHostMutationVerdict.Accepted(report)
        }
        return reject(node, mergedDiff, report.message, report)
    }

    private fun reject(
        node: DagNode,
        mergedDiff: String,
        reason: String,
        report: CompletionGateReport?
    ): SelfHostMutationVerdict.Rejected {
        val reversal = reverse(mergedDiff)
        return SelfHostMutationVerdict.Rejected(
            nodeId = node.id,
            reason = reason,
            report = report,
            reverted = reversal
        )
    }

    private fun reverse(mergedDiff: String): SelfHostMutationReversal {
        if (mergedDiff.isBlank()) {
            return SelfHostMutationReversal(false, "no recorded diff to reverse")
        }
        val result = runCatching {
            gitRunner.run(GitWorktreeOperation.REVERSE_APPLY_PATCH, repoRoot, input = mergedDiff)
        }.getOrElse { failure ->
            return SelfHostMutationReversal(false, "reverse apply failed to start: ${failure.javaClass.simpleName}")
        }
        return if (result.exitCode == 0) {
            SelfHostMutationReversal(true, "merged mutation reversed")
        } else {
            SelfHostMutationReversal(false, "reverse apply failed with exit=${result.exitCode}")
        }
    }
}

/** Whether a merged mutation survived verification, and what was done if it did not. */
sealed interface SelfHostMutationVerdict {

    data class Accepted(val report: CompletionGateReport) : SelfHostMutationVerdict

    data class Rejected(
        val nodeId: String,
        val reason: String,
        val report: CompletionGateReport?,
        val reverted: SelfHostMutationReversal
    ) : SelfHostMutationVerdict {

        /**
         * True when the tree still carries the rejected change. The operator has
         * to be told this explicitly — it is the one outcome where a refused
         * mutation left something behind.
         */
        val treeIsDirty: Boolean get() = !reverted.ok
    }

    fun evidenceLine(): String = when (this) {
        is Accepted -> "self_host_mutation_verified accepted=true gates=${report.gateResults.size}"
        is Rejected -> "self_host_mutation_verified accepted=false reverted=${reverted.ok} " +
            "dirty=$treeIsDirty reason=${reason.replace('\n', ' ').take(240)}"
    }
}

data class SelfHostMutationReversal(val ok: Boolean, val message: String)
