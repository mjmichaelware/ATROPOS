package atropos.core.planning

import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class InternalPlanningGraphPlugin(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val store: DagStore = DagStore(repoRoot),
    private val readinessCalculator: InternalReadinessCalculator = InternalReadinessCalculator()
) : PlanningGraphPlugin {
    private val evidenceDir = repoRoot.resolve(".atropos/planning/evidence")

    override fun getReadyNodes(projectId: String, graphVersion: String): List<ReadyNode> {
        val dag = store.readDag(graphVersion) ?: return emptyList()
        return readinessCalculator.readyNodes(dag).map { node ->
            ReadyNode(
                projectId = dag.projectId ?: projectId,
                graphVersion = dag.id,
                nodeId = node.id,
                label = node.label,
                territory = Territory(readPaths = node.territory, writePaths = node.territory),
                dependencies = node.dependencies,
                action = node.action,
                actionPayload = node.actionPayload
            )
        }
    }

    override fun claimNode(nodeId: String, executorId: String, territory: Territory): NodeClaim {
        val node = store.claimNode(nodeId, executorId)
            ?: return NodeClaim(false, nodeId, executorId, territory, reason = "unable to claim node")
        return NodeClaim(
            accepted = true,
            nodeId = nodeId,
            executorId = executorId,
            territory = territory,
            claimToken = node.claimToken,
            expiresAt = node.claimExpiresAt
        )
    }

    override fun submitEvidence(nodeId: String, evidence: ExecutionEvidence): EvidenceReceipt {
        Files.createDirectories(evidenceDir)
        val file = evidenceDir.resolve("$nodeId.log")
        val line = buildString {
            append("timestamp="); append(evidence.recordedAt); append('\t')
            append("kind="); append(evidence.kind); append('\t')
            append("detail="); append(evidence.detail.replace('\n', ' ')); append('\t')
            append("paths="); append(evidence.relatedPaths.joinToString("|"))
            append('\n')
        }
        Files.writeString(
            file,
            line,
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )
        return EvidenceReceipt(nodeId, true, evidence.recordedAt, file.toString())
    }

    override fun completeNode(nodeId: String, result: NodeResult) {
        val node = store.readNode(nodeId) ?: return
        store.writeNode(
            node.copy(
                state = result.finalState,
                result = result.result,
                failureReason = result.failureReason,
                lastMessage = result.message,
                claimToken = null,
                claimOwner = null,
                claimExpiresAt = null,
                finishedAt = result.finishedAt
            )
        )
    }
}
