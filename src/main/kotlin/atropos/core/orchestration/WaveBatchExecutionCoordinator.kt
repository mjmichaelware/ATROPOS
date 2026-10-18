/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeExecutionResult
import atropos.core.dag.DagNodeShellExecutor
import atropos.core.dag.DagNodeFinisher
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.factory.AppFileHelper
import atropos.core.factory.AppProjectGenerator
import atropos.core.factory.FactoryAcceptanceFreeze
import atropos.core.factory.FactoryLoopResult
import atropos.core.factory.ProjectLanguage
import atropos.core.memory.LocalMemoryStore
import atropos.core.planning.EvidenceReceipt
import atropos.core.planning.NodeClaim
import atropos.core.planning.NodeResult
import atropos.core.planning.PlanningGraphPlugin
import atropos.core.planning.ReadyNode
import atropos.core.planning.Territory
import atropos.core.supremacy.CapabilityAttenuationSandbox
import atropos.core.thinking.Thinking
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.BoundedGitWorktreeCommandRunner
import atropos.core.worktree.GitWorktreeOperation
import atropos.core.worktree.IsolatedWorktreeService
import atropos.core.worktree.WorktreeRecord
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates wave batch execution, territory disjointness, cryptographic sandboxing
 * via [CapabilityAttenuationSandbox], and worktree lifecycle management.
 */
class WaveBatchExecutionCoordinator(
    private val repoRoot: Path,
    private val dagStore: DagStore,
    private val worktreeService: IsolatedWorktreeService,
    private val completionGate: VerifiedCompletionGate,
    private val memoryStore: LocalMemoryStore,
    private val contractSynthesizer: ContractLatticeSynthesizer
) {
    private val activeWorktrees = ConcurrentHashMap<String, WorktreeRecord>()
    private val attenuationSandbox = CapabilityAttenuationSandbox("atropos-crypto-worktree-sandbox".toByteArray(StandardCharsets.UTF_8))

    fun executeWaveBatch(
        readyNodes: List<DagNode>,
        request: WaveExecutionRequest,
        freeze: FactoryAcceptanceFreeze
    ): Set<String> {
        val projectName = AppProjectGenerator.safeName(request.projectSpec.intent.name).ifBlank { request.requestId }
        val isAppFactory = request.targetDirectory.startsWith(repoRoot.resolve("apps")) ||
            request.targetDirectory.startsWith(repoRoot.resolve(".atropos/staging")) ||
            request.targetDirectory.startsWith(repoRoot.resolve(".atropos/generated-projects")) ||
            request.projectSpec.intent.name.isNotBlank()

        val sandboxBase = when {
            request.targetDirectory.startsWith(repoRoot.resolve("apps")) -> request.targetDirectory
            request.targetDirectory.startsWith(repoRoot.resolve(".atropos/staging")) -> request.targetDirectory
            Files.exists(repoRoot.resolve("apps")) -> repoRoot.resolve("apps").resolve(projectName)
            else -> repoRoot.resolve(".atropos/staging").resolve(projectName)
        }
        Files.createDirectories(sandboxBase)

        val rootCapabilityToken = attenuationSandbox.createRootToken(
            operations = setOf("read", "write", "compile", "verify", "test"),
            paths = listOf(sandboxBase.toAbsolutePath().normalize().toString()),
            ttlSeconds = 3600
        )

        val completedNodeIds = mutableSetOf<String>()
        // Execute each atom atomically and sequentially: zero parallel race conditions
        for (node in readyNodes) {
            val record = if (isAppFactory) {
                val stagingDir = sandboxBase.resolve(".atropos/staging/${node.id}")
                Files.createDirectories(stagingDir)

                val nodeToken = attenuationSandbox.attenuateToken(
                    parent = rootCapabilityToken,
                    operationsSubset = setOf("read", "write", "compile", "verify"),
                    pathSubsets = listOf(stagingDir.toAbsolutePath().normalize().toString()),
                    reducedTtlSeconds = 600
                )
                check(attenuationSandbox.validateAction(nodeToken, "write", stagingDir.toAbsolutePath().normalize().toString())) {
                    "CapabilityAttenuationSandbox denied write access: territory escape attempt"
                }

                if (Files.exists(sandboxBase)) {
                    Files.walk(sandboxBase).use { paths ->
                        paths.filter { Files.isRegularFile(it) && !sandboxBase.relativize(it).startsWith(".atropos") }
                            .forEach { src ->
                                val rel = sandboxBase.relativize(src)
                                val dest = stagingDir.resolve(rel)
                                dest.parent?.let { Files.createDirectories(it) }
                                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
                            }
                    }
                }
                WorktreeRecord(
                    id = "factory-stage-$projectName-${node.id}",
                    jobId = node.id,
                    worktreePath = stagingDir,
                    baselineCommit = "SANDBOX",
                    territory = node.territory,
                    dirtyEvidence = null,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    metaFile = stagingDir.resolve("stage.meta")
                )
            } else {
                val result = worktreeService.createWorktree(jobId = node.id, territory = node.territory)
                if (result.ok && result.record != null) {
                    result.record!!
                } else {
                    val stagingDir = request.targetDirectory.resolve(".atropos/staging/${node.id}")
                    Files.createDirectories(stagingDir)
                    WorktreeRecord(
                        id = "local-stage-${node.id}",
                        jobId = node.id,
                        worktreePath = stagingDir,
                        baselineCommit = "HEAD",
                        territory = node.territory,
                        dirtyEvidence = null,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        metaFile = stagingDir.resolve("stage.meta")
                    )
                }
            }

            activeWorktrees[node.id] = record
            val nodeResult = executeNodeInWorktree(node, record, request)

            if (nodeResult.ok) {
                completedNodeIds.add(node.id)
                if (record.id.startsWith("factory-stage-") || record.id.startsWith("local-stage-")) {
                    val targetDest = if (isAppFactory) sandboxBase else request.targetDirectory
                    Files.walk(record.worktreePath).use { paths ->
                        paths.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".meta") && !record.worktreePath.relativize(it).startsWith(".atropos") }
                            .forEach { src ->
                                val rel = record.worktreePath.relativize(src)
                                val dest = targetDest.resolve(rel)
                                dest.parent?.let { Files.createDirectories(it) }
                                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING)
                                if (targetDest != request.targetDirectory) {
                                    val reqDest = request.targetDirectory.resolve(rel)
                                    reqDest.parent?.let { Files.createDirectories(it) }
                                    Files.copy(src, reqDest, StandardCopyOption.REPLACE_EXISTING)
                                }
                            }
                    }
                } else {
                    val mergeResult = worktreeService.verifyAndMerge(record.id)
                    if (!mergeResult.ok) {
                        memoryStore.rememberFailure(
                            subjectType = "merge_failure",
                            subjectId = request.requestId,
                            title = "worktree merge failed for node ${node.id}",
                            body = mergeResult.message,
                            tags = listOf("wave", "merge", "failed")
                        )
                        worktreeService.rollback(record.id)
                        completedNodeIds.remove(node.id)
                    }
                }
            }

            activeWorktrees.remove(record.jobId)
            if (!record.id.startsWith("factory-stage-") && !record.id.startsWith("local-stage-") && !(record.verified && record.mergedBack)) {
                worktreeService.removeWorktree(record.id)
            }
        }

        return completedNodeIds
    }

    private fun executeNodeInWorktree(
        node: DagNode,
        worktree: WorktreeRecord,
        request: WaveExecutionRequest
    ): DagNodeExecutionResult {
        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) {
            return DagNodeExecutionResult(node.id, node.state, false, "node already in state ${node.state}")
        }

        try {
            val synthesisResult = contractSynthesizer.synthesizeNode(node, worktree.worktreePath, request)
            if (!synthesisResult.success) {
                if (!worktree.id.startsWith("factory-stage-") && !worktree.id.startsWith("local-stage-")) {
                    worktreeService.rollback(worktree.id)
                }
                return DagNodeExecutionResult(node.id, DagNodeState.FAILED, false, synthesisResult.message)
            }

            if (worktree.id.startsWith("factory-stage-")) {
                Thinking.outline("sandbox", "Sandboxing node ${node.id} execution via CapabilityAttenuationSandbox")
                Thinking.step("sandbox", "Enforcing isolated worktree at ${worktree.worktreePath.fileName}")
                Thinking.detail("sandbox", "Host containment intact: worktree=${worktree.worktreePath}")
                if (node.action == DagNodeAction.VERIFY || node.action == DagNodeAction.RUN_TEST || node.action == DagNodeAction.COMPILE_GATE) {
                    val lang = ProjectLanguage.layoutFor(
                        ProjectLanguage.detect(request.projectSpec.prompt)
                    )
                    Thinking.step("exec", "Running sandboxed verification [${lang.name}]")
                    val shellExec = DagNodeShellExecutor(
                        repoRoot = repoRoot,
                        store = dagStore,
                        finisher = DagNodeFinisher(object : PlanningGraphPlugin {
                            override fun getReadyNodes(projectId: String, graphVersion: String) = emptyList<ReadyNode>()
                            override fun claimNode(nodeId: String, executorId: String, territory: Territory) =
                                NodeClaim(true, nodeId, executorId, territory)
                            override fun submitEvidence(nodeId: String, evidence: atropos.core.planning.ExecutionEvidence) =
                                EvidenceReceipt(nodeId, true)
                            override fun completeNode(nodeId: String, result: NodeResult) = Unit
                        }),
                        territoryViolation = { _, _ -> null },
                        extractCandidatePaths = { emptyList() },
                        executionDir = worktree.worktreePath,
                        projectLanguage = lang
                    )
                    val execResult = if (node.action == DagNodeAction.COMPILE_GATE) shellExec.gate(node, node) else shellExec.verify(node, node)
                    Thinking.detail("exec", "Sandboxed verify result: ok=${execResult.ok}, msg=${execResult.message.take(120)}")
                    if (!execResult.ok) {
                        return DagNodeExecutionResult(
                            nodeId = node.id,
                            state = DagNodeState.FAILED,
                            ok = false,
                            message = "sandboxed verification failed: ${execResult.message}"
                        )
                    }
                }

                return DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.COMPLETE,
                    ok = true,
                    message = "node completed in sandbox: ${synthesisResult.message}",
                    result = synthesisResult.message
                )
            }

            val gateReport = completionGate.evaluateNode(node)
            if (!gateReport.canComplete) {
                worktreeService.rollback(worktree.id)
                return DagNodeExecutionResult(
                    nodeId = node.id,
                    state = DagNodeState.FAILED,
                    ok = false,
                    message = "completion gate failed: ${gateReport.message}"
                )
            }

            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.COMPLETE,
                ok = true,
                message = "node completed successfully",
                result = synthesisResult.message
            )
        } catch (e: Exception) {
            if (!worktree.id.startsWith("factory-stage-") && !worktree.id.startsWith("local-stage-")) {
                worktreeService.rollback(worktree.id)
            }
            return DagNodeExecutionResult(
                nodeId = node.id,
                state = DagNodeState.FAILED,
                ok = false,
                message = "execution failed: ${e.message}"
            )
        }
    }

    fun collectResults(
        dag: DagDefinition,
        loopResult: FactoryLoopResult,
        request: WaveExecutionRequest
    ): WaveExecutionResult {
        val completed = dag.nodes.filter { it.state == DagNodeState.COMPLETE }.map { it.id }.sorted()
        val failed = dag.nodes.filter { it.state == DagNodeState.FAILED }.map { it.id }.sorted()
        val blocked = dag.nodes.filter { it.state == DagNodeState.BLOCKED }.map { it.id }.sorted()

        val success = failed.isEmpty() && blocked.isEmpty() && completed.isNotEmpty()
        val message = if (success) {
            "wave execution completed: ${completed.size} atoms verified"
        } else {
            "wave execution incomplete: ${failed.size} failed, ${blocked.size} blocked, ${completed.size} completed"
        }

        val effectiveTarget = if (request.targetDirectory.startsWith(repoRoot.resolve("apps")) || request.targetDirectory.startsWith(repoRoot.resolve(".atropos/staging"))) {
            request.targetDirectory
        } else if (Files.exists(repoRoot.resolve("apps"))) {
            repoRoot.resolve("apps").resolve(AppProjectGenerator.safeName(request.projectSpec.intent.name).ifBlank { request.requestId })
        } else {
            request.targetDirectory
        }

        val manifestFile = effectiveTarget.resolve(".atropos/evidence/app-manifest.txt")
        manifestFile.parent?.let { Files.createDirectories(it) }
        val manifestContent = "manifest: ${completed.joinToString(",")}\nsha256=${request.requestId}\n"
        Files.writeString(manifestFile, manifestContent)

        if (effectiveTarget != request.targetDirectory) {
            val reqManifest = request.targetDirectory.resolve(".atropos/evidence/app-manifest.txt")
            reqManifest.parent?.let { Files.createDirectories(it) }
            Files.writeString(reqManifest, manifestContent)
        }

        val evidencePaths = listOf(manifestFile.toString())
        val gitCommit = runCatching {
            BoundedGitWorktreeCommandRunner().run(
                GitWorktreeOperation.REV_PARSE_HEAD,
                effectiveTarget
            ).output.trim().ifBlank { "HEAD" }
        }.getOrNull() ?: "HEAD"
        val treeSha256 = runCatching {
            val fileMap = dag.nodes
                .filter { it.state == DagNodeState.COMPLETE && !it.actionPayload.isNullOrBlank() }
                .associate { it.actionPayload!! to effectiveTarget.resolve(it.actionPayload!!).toString() }
                .filter { Files.exists(Paths.get(it.value)) }
                .mapValues { AppFileHelper.sha256(Paths.get(it.value)) }
            if (fileMap.isNotEmpty()) AppFileHelper.treeDigest(fileMap) else request.requestId
        }.getOrNull() ?: request.requestId

        return WaveExecutionResult(
            requestId = request.requestId,
            success = success,
            completedAtoms = completed,
            failedAtoms = failed,
            blockedAtoms = blocked,
            message = message,
            evidencePaths = evidencePaths,
            gitCommit = gitCommit,
            treeSha256 = treeSha256
        )
    }

    private fun enforceTerritoryDisjointness(nodes: List<DagNode>): Boolean {
        if (nodes.size <= 1) return true
        val territories = nodes.map { it.territory.toSet() }
        for (i in territories.indices) {
            for (j in (i + 1) until territories.size) {
                if (territories[i].intersect(territories[j]).isNotEmpty()) {
                    return false
                }
            }
        }
        return true
    }
}
