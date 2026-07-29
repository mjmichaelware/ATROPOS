package atropos.core.agent

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.worktree.IsolatedWorktreeService
import java.nio.file.Files
import java.nio.file.Path

class SelfHostWorktreeNodeExecutor(
    private val repoRoot: Path,
    private val dagStore: DagStore = DagStore(repoRoot),
    private val worktreeService: IsolatedWorktreeService = IsolatedWorktreeService(repoRoot),
    private val hasher: SelfHostFileHasher = SelfHostFileHasher()
) {
    fun canExecute(node: DagNode): Boolean =
        node.action == DagNodeAction.CREATE_FILE || node.action == DagNodeAction.EDIT_FILE

    fun execute(node: DagNode): DagNodeExecutionResult {
        val mutation = parseMutation(node.actionPayload)
            ?: return fail(node, "unsupported file mutation payload")
        val territoryFailure = territoryViolation(node, mutation.path)
        if (territoryFailure != null) return fail(node, territoryFailure)

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
            target.parent?.let { Files.createDirectories(it) }
            Files.writeString(target, mutation.content + "\n")
            val intentToAdd = ProcessBuilder("git", "add", "-N", mutation.path.toString())
                .directory(worktree.worktreePath.toFile())
                .redirectErrorStream(true)
                .start()
            val addOutput = intentToAdd.inputStream.bufferedReader().readText()
            if (intentToAdd.waitFor() != 0) {
                return fail(node, "worktree intent-to-add failed: ${addOutput.take(200)}")
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

    private fun parseMutation(payload: String?): ParsedMutation? {
        val body = payload?.trim().orEmpty()
        val explicit = body.split("::", limit = 2)
        if (explicit.size != 2 || explicit[0].isBlank()) return null
        val path = Path.of(explicit[0].trim())
        if (path.isAbsolute) return null
        val content = explicit[1].trim()
        if (content.isBlank()) return null
        return ParsedMutation(path.normalize(), content)
    }

    private data class ParsedMutation(
        val path: Path,
        val content: String
    )
}
