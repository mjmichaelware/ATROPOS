/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import java.nio.file.Path
import java.util.UUID

/**
 * Builds [ActionProposal]s for patch inspection and application.
 *
 * Construction only. No policy logic, no verdict — that authority belongs to
 * [ExecutionPolicyEngine] behind [BoundedAgencyGate]. Its single responsibility
 * is turning a patch intent into something the gate can judge.
 *
 * Each builder reproduces the [ExecutionPolicyRequest] `AgentPatchStore` used to
 * assemble inline, field for field. `targetPaths` matters especially: the
 * engine denies a `PATCH_APPLY` with no declared targets, so dropping it here
 * would silently change a verdict.
 */
object PatchActionProposals {
    /** `git apply --check` — proves a patch would apply, mutating nothing. */
    fun applyCheck(diffFile: Path, repoRoot: Path): ActionProposal =
        patchProposal(
            command = listOf("git", "apply", "--check", diffFile.toString()),
            repoRoot = repoRoot,
            targetPaths = listOf(repoRoot.relativize(diffFile).toString())
        )

    /** `git apply` — the mutation itself. */
    fun apply(diffFile: Path, repoRoot: Path): ActionProposal =
        patchProposal(
            command = listOf("git", "apply", diffFile.toString()),
            repoRoot = repoRoot,
            targetPaths = listOf(repoRoot.relativize(diffFile).toString())
        )

    /**
     * Pre-authorisation for applying a stored patch.
     *
     * Declares the paths the patch actually touches rather than the patch file,
     * so the engine judges the blast radius of the mutation and not the diff's
     * own location.
     */
    fun applyStored(patchFile: Path, repoRoot: Path, touchedPaths: List<String>): ActionProposal =
        patchProposal(
            command = listOf("git", "apply", patchFile.toString()),
            repoRoot = repoRoot,
            targetPaths = touchedPaths
        )

    /** Read-only `git status` scoped to the paths a patch touched. */
    fun statusForPaths(paths: List<String>, repoRoot: Path): ActionProposal =
        ActionProposal(
            id = nextId(),
            actionClass = PolicyActionClass.GIT,
            command = listOf("git", "status", "--porcelain", "--untracked-files=all", "--") + paths,
            cwd = repoRoot.toString(),
            targetPaths = paths
        )

    private fun patchProposal(
        command: List<String>,
        repoRoot: Path,
        targetPaths: List<String>
    ): ActionProposal = ActionProposal(
        id = nextId(),
        actionClass = PolicyActionClass.PATCH_APPLY,
        command = command,
        cwd = repoRoot.toString(),
        targetPaths = targetPaths
    )

    private fun nextId(): String = "patch-" + UUID.randomUUID().toString().take(12)
}
