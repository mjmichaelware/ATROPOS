package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.policy.ActionActor
import atropos.core.policy.ActionProposal
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.PolicyActionClass
import atropos.core.territory.GrantResult
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.file.Files
import java.nio.file.Path

class SelfHostWorktreeNodeExecutor(
    private val repoRoot: Path,
    private val dagStore: DagStore = DagStore(repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val hasher: SelfHostFileHasher = SelfHostFileHasher(),
    private val mutationParser: SelfHostMutationPayloadParser = SelfHostMutationPayloadParser(),
    private val diffInspector: SelfHostWorktreeDiffInspector = SelfHostWorktreeDiffInspector(),
    private val territoryGrants: TerritoryGrantService =
        TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot))),
    private val agencyGate: BoundedAgencyGate = BoundedAgencyGate(
        policyEngine = ExecutionPolicyEngine(repoRoot),
        territory = territoryGrants
    )
) {
    fun canExecute(node: DagNode): Boolean =
        node.action == DagNodeAction.CREATE_FILE || node.action == DagNodeAction.EDIT_FILE

    fun execute(node: DagNode): DagNodeExecutionResult {
        val mutation = mutationParser.parse(node.actionPayload)
            ?: return fail(node, "unsupported file mutation payload")
        val territoryFailure = territoryViolation(node, mutation.path)
        if (territoryFailure != null) return fail(node, territoryFailure)
        val expectedOutputFailure = expectedOutputViolation(node, mutation.path)
        if (expectedOutputFailure != null) return fail(node, expectedOutputFailure)

        val actor = ActionActor.HierarchyNode(role = "self-host-worktree", nodeId = node.id)
        when (val grant = territoryGrants.grantToNode(ActionActor.HumanOwner, actor, node.territory)) {
            is GrantResult.Refused -> return fail(node, "self-host mutation refused by territory grant: ${grant.reason}")
            is GrantResult.Granted -> Unit
        }

        val running = dagStore.writeNode(
            node.copy(
                state = DagNodeState.RUNNING,
                claimOwner = "self-host-worktree",
                lastMessage = "self-host isolated worktree mutation started"
            )
        )
        val created = worktreeService.createWorktree(running.id, running.territory)
        val worktree = created.record ?: return fail(running, created.message)
        return try {
            if (!Files.exists(worktree.worktreePath.resolve(".git"))) {
                return fail(running, "isolated worktree was not initialized: ${worktree.id}")
            }
            val target = worktree.worktreePath.resolve(mutation.path).normalize()
            val worktreeRoot = worktree.worktreePath.toAbsolutePath().normalize()
            if (!target.startsWith(worktreeRoot)) {
                return fail(node, "worktree path escaped root: ${mutation.path}")
            }
            val agency = agencyGate.evaluate(
                ActionProposal(
                    id = "self-host-write-${node.id}",
                    actionClass = PolicyActionClass.FILE_MUTATION,
                    actor = actor,
                    cwd = worktree.worktreePath.toString(),
                    targetPaths = listOf(mutation.path.toString()),
                    metadata = mapOf("origin" to "self-host-worktree-node")
                )
            )
            if (agency.disposition != atropos.core.policy.AgencyDisposition.ALLOWED) {
                return fail(running, "self-host mutation refused by bounded agency: ${agency.reason}")
            }
            if (!worktreeService.writeFile(worktree.id, mutation.path.toString(), mutation.content + "\n")) {
                return fail(running, "isolated worktree refused file mutation: ${mutation.path}")
            }
            val staged = worktreeService.intentToAdd(worktree.id, mutation.path.toString())
            if (!staged.ok) {
                return fail(node, "worktree intent-to-add failed: ${staged.message.take(200)}")
            }
            val changedPaths = diffInspector.changedPaths(worktree.baselineCommit, worktree.worktreePath, mutation.path)
            if (changedPaths.isEmpty()) {
                return fail(running, "self-host mutation produced no source diff: ${mutation.path}")
            }
            val changedOutputFailure = changedPaths
                .map(::normalizeRelative)
                .firstOrNull { changed -> node.expectedOutputs.isNotEmpty() && changed !in node.expectedOutputs.map(::normalizeRelative) }
            if (changedOutputFailure != null) {
                return fail(running, "self-host mutation changed undeclared output: $changedOutputFailure")
            }
            val merged = worktreeService.verifyAndMerge(worktree.id, "git diff --check")
            if (!merged.ok) return fail(node, merged.message)

            val completed = dagStore.writeNode(
                running.copy(
                    state = DagNodeState.COMPLETE,
                    claimOwner = null,
                    claimToken = null,
                    claimExpiresAt = null,
                    result = "worktree=${worktree.id} merged=true path=${mutation.path} sha256=${hasher.sha256(repoRoot.resolve(mutation.path)) ?: "missing"}",
                    lastMessage = merged.message,
                    finishedAt = java.time.Instant.now()
                )
            )
            DagNodeExecutionResult(
                nodeId = node.id,
                state = completed.state,
                ok = true,
                message = merged.message,
                result = completed.result
            )
        } catch (failure: Exception) {
            fail(running, failure.message ?: "self-host worktree mutation failed")
        }
    }

    private fun fail(node: DagNode, reason: String): DagNodeExecutionResult {
        val failed = dagStore.writeNode(
            node.copy(
                state = DagNodeState.FAILED,
                claimOwner = null,
                claimToken = null,
                claimExpiresAt = null,
                failureReason = reason,
                lastMessage = reason,
                finishedAt = java.time.Instant.now()
            )
        )
        return DagNodeExecutionResult(node.id, failed.state, false, reason)
    }

    private fun territoryViolation(node: DagNode, path: Path): String? {
        val normalized = path.toString().replace('\\', '/').trimStart('/')
        if (node.territory.isEmpty()) return "node declared no territory"
        val inside = node.territory.any { territory ->
            val allowed = territory.replace('\\', '/').trim('/').trimEnd('/')
            normalized == allowed || normalized.startsWith("$allowed/")
        }
        return if (inside) null else "territory violation before worktree mutation: $normalized"
    }

    private fun expectedOutputViolation(node: DagNode, path: Path): String? {
        if (node.expectedOutputs.isEmpty()) return null
        val normalizedPath = normalizeRelative(path.toString())
        val expected = node.expectedOutputs.map(::normalizeRelative)
        return if (normalizedPath in expected) null
        else "mutation target is not a declared expected output: $normalizedPath"
    }

    private fun normalizeRelative(path: String): String =
        path.replace('\\', '/').trimStart('/').trimEnd('/')

}
