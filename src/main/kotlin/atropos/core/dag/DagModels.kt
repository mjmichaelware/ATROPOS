package atropos.core.dag

import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

enum class RequirementType {
    OBJECTIVE, INVARIANT, CONSTRAINT, FEATURE, FUNCTION, COMMAND, ENDPOINT,
    INTERFACE, SCREEN, COMPONENT, MODEL, FIELD, ATTRIBUTE, STATE, TRANSITION,
    EVENT, SERVICE, ADAPTER, PROVIDER, STORE, SCHEMA, FILE_OBLIGATION,
    SECURITY_RULE, PERMISSION_RULE, ERROR_CONDITION, FALLBACK, RECOVERY,
    DEPLOYMENT_TARGET, PLATFORM_REQUIREMENT, TEST, FIXTURE, QUALITY_GATE,
    ARTIFACT, ACCEPTANCE_PROOF, DOCUMENTATION
}

enum class RequirementClassification { EXPLICIT, INFERRED }

enum class ImplementationState { ABSENT, PARTIAL, IMPLEMENTED, VERIFIED, ACCEPTED, BLOCKED, SUPERSEDED }

enum class DAGNodeState { PENDING, RUNNABLE, IN_PROGRESS, COMPLETED, FAILED, BLOCKED }

data class SourceDocument(
    val id: String,
    val sha256: String,
    val size: Long,
    val format: String,
    val originalPath: String,
    val ingestionTime: Instant = Instant.now(),
    val version: Int = 1,
    val sections: List<SourceSection> = emptyList()
)

data class SourceSection(
    val sectionId: String,
    val heading: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val coordinates: String
)

data class ExtractedRequirement(
    val id: String = "req-${UUID.randomUUID().toString().take(12)}",
    val parentIds: List<String> = emptyList(),
    val sourceDocumentId: String = "",
    val sourceSectionId: String = "",
    val sourceCoordinates: String = "",
    val canonicalWording: String,
    val normalizedWording: String = "",
    val type: RequirementType = RequirementType.FEATURE,
    val classification: RequirementClassification = RequirementClassification.EXPLICIT,
    val priority: Int = 5,
    val dependencies: List<String> = emptyList(),
    val dependents: List<String> = emptyList(),
    val implementationState: ImplementationState = ImplementationState.ABSENT,
    val permittedFiles: List<String> = emptyList(),
    val expectedSymbols: List<String> = emptyList(),
    val testObligations: List<String> = emptyList(),
    val acceptanceCriteria: String = "",
    val executionState: DAGNodeState = DAGNodeState.PENDING
)

data class DAGNode(
    val id: String = "node-${UUID.randomUUID().toString().take(12)}",
    val requirementId: String,
    val parentIds: List<String> = emptyList(),
    val children: List<String> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val state: DAGNodeState = DAGNodeState.PENDING,
    val implementationFiles: List<String> = emptyList(),
    val testFiles: List<String> = emptyList(),
    val hash: String = ""
) {
    fun isRunnable(): Boolean = state == DAGNodeState.PENDING || state == DAGNodeState.RUNNABLE
}

data class DAG(
    val id: String = "dag-${UUID.randomUUID().toString().take(12)}",
    val nodes: Map<String, DAGNode> = emptyMap(),
    val sourceDocumentIds: List<String> = emptyList(),
    val version: Int = 1,
    val sourceFingerprint: String = "",
    val schemaVersion: String = "1.0",
    val createdAt: Instant = Instant.now()
) {
    fun runnableNodes(): List<DAGNode> {
        return nodes.values.filter { node ->
            node.isRunnable() && node.dependencies.all { depId ->
                nodes[depId]?.state == DAGNodeState.COMPLETED
            }
        }
    }

    fun blockedNodes(): List<DAGNode> {
        return nodes.values.filter { node ->
            node.state == DAGNodeState.PENDING && node.dependencies.any { depId ->
                nodes[depId]?.state == DAGNodeState.FAILED || nodes[depId]?.state == DAGNodeState.BLOCKED
            }
        }
    }

    val isComplete: Boolean get() = nodes.values.all { it.state == DAGNodeState.COMPLETED || it.state == DAGNodeState.FAILED }

    companion object {
        fun fingerprint(vararg sources: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            sources.sorted().forEach { digest.update(it.toByteArray(Charsets.UTF_8)) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

data class HIGReport(
    val absent: Int,
    val partial: Int,
    val implemented: Int,
    val verified: Int,
    val total: Int,
    val hig: Double
) {
    val higFormatted: String get() = "HIG=${"%.2f".format(hig)}"

    companion object {
        fun compute(requirements: List<ExtractedRequirement>): HIGReport {
            val absent = requirements.count { it.implementationState == ImplementationState.ABSENT }
            val partial = requirements.count { it.implementationState == ImplementationState.PARTIAL }
            val implemented = requirements.count { it.implementationState == ImplementationState.IMPLEMENTED }
            val verified = requirements.count { it.implementationState == ImplementationState.VERIFIED || it.implementationState == ImplementationState.ACCEPTED }
            val total = requirements.size
            val hig = if (total == 0) 0.0 else (absent + partial).toDouble() / total.toDouble()
            return HIGReport(absent, partial, implemented, verified, total, hig)
        }
    }
}

enum class DagNodeState {
    PENDING,
    READY,
    CLAIMED,
    RUNNING,
    VERIFYING,
    COMPLETE,
    FAILED,
    BLOCKED,
    NOT_APPLICABLE,
    CANCELLED;

    val terminal: Boolean
        get() = this in setOf(COMPLETE, FAILED, BLOCKED, NOT_APPLICABLE, CANCELLED)
}

enum class DagNodeAction {
    CREATE_FILE,
    EDIT_FILE,
    RUN_COMMAND,
    RUN_TEST,
    RUN_BUILD,
    VERIFY,
    PROVIDER_CALL,
    POLICY_CHECK,
    SECRET_CHECK,
    TERRITORY_CHECK,
    COMPILE_GATE,
    SMOKE_GATE,
    ACCEPTANCE_GATE
}

data class DagNode(
    val id: String,
    val dagId: String? = null,
    val label: String,
    val dependencies: List<String> = emptyList(),
    val territory: List<String> = emptyList(),
    val action: DagNodeAction = DagNodeAction.RUN_COMMAND,
    val actionPayload: String? = null,
    val expectedOutputs: List<String> = emptyList(),
    val maxAttempts: Int = 2,
    val retryDelaySeconds: Long = 15L,
    val state: DagNodeState = DagNodeState.PENDING,
    val claimToken: String? = null,
    val claimOwner: String? = null,
    val claimExpiresAt: Instant? = null,
    val attempts: Int = 0,
    val result: String? = null,
    val failureReason: String? = null,
    val childJobId: String? = null,
    val lastMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finishedAt: Instant? = null,
    val metaFile: Path
) {
    fun isReady(dependencyStates: Map<String, DagNodeState>): Boolean {
        if (state != DagNodeState.PENDING && state != DagNodeState.READY) return false
        return dependencies.all { depId ->
            val depState = dependencyStates[depId]
            depState == DagNodeState.COMPLETE || depState == DagNodeState.NOT_APPLICABLE
        }
    }

    fun render(): String = buildString {
        appendLine("node id: $id")
        appendLine("dag id: ${dagId ?: "none"}")
        appendLine("label: $label")
        appendLine("dependencies: ${dependencies.joinToString(", ").ifEmpty { "none" }}")
        appendLine("territory: ${territory.joinToString(", ").ifEmpty { "none" }}")
        appendLine("action: $action")
        appendLine("state: $state")
        appendLine("attempts: $attempts/$maxAttempts")
        appendLine("claim owner: ${claimOwner ?: "none"}")
        appendLine("claim token: ${claimToken ?: "none"}")
        appendLine("claim expires: ${claimExpiresAt ?: "none"}")
        appendLine("child job: ${childJobId ?: "none"}")
        appendLine("result: ${result ?: "none"}")
        appendLine("failure: ${failureReason ?: "none"}")
        appendLine("last message: ${lastMessage ?: "none"}")
        appendLine("created: $createdAt")
        appendLine("updated: $updatedAt")
        appendLine("finished: ${finishedAt ?: "none"}")
        appendLine("record file: $metaFile")
    }.trimEnd()
}

data class DagDefinition(
    val id: String,
    val label: String,
    val projectId: String? = null,
    val nodes: List<DagNode>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metaFile: Path
) {
    fun findNode(nodeId: String): DagNode? = nodes.find { it.id == nodeId }

    fun findReadyNodes(): List<DagNode> {
        val allStates = nodes.associate { it.id to it.state }
        return nodes.filter { it.isReady(allStates) }
    }

    fun findParallelReadyNodes(): List<List<DagNode>> {
        val ready = findReadyNodes()
        val groups = mutableListOf<List<DagNode>>()
        val assigned = mutableSetOf<String>()
        for (node in ready) {
            if (node.id in assigned) continue
            val group = mutableListOf(node)
            assigned.add(node.id)
            for (other in ready) {
                if (other.id in assigned) continue
                val territoryOverlap = node.territory.any { territory -> other.territory.contains(territory) }
                if (!territoryOverlap) {
                    group.add(other)
                    assigned.add(other.id)
                }
            }
            groups.add(group)
        }
        return groups
    }

    fun render(): String = buildString {
        appendLine("DAG: $id ($label)")
        appendLine("project: ${projectId ?: "none"}")
        appendLine("nodes: ${nodes.size}")
        appendLine()
        nodes.forEach { node ->
            appendLine("  ${node.id}: ${node.label} [${node.state}] deps=[${node.dependencies.joinToString(", ")}]")
        }
        appendLine()
        appendLine("ready: ${findReadyNodes().map { it.id }}")
    }.trimEnd()
}

data class DagExecutionResult(
    val dagId: String,
    val ok: Boolean,
    val completedNodes: Int,
    val failedNodes: Int,
    val blockedNodes: Int,
    val message: String,
    val nodeResults: List<DagNodeExecutionResult>? = emptyList()
)

data class DagNodeExecutionResult(
    val nodeId: String,
    val state: DagNodeState,
    val ok: Boolean,
    val message: String,
    val result: String? = null
)

data class DagStatus(
    val dagId: String,
    val totalNodes: Int,
    val completedNodes: Int,
    val failedNodes: Int,
    val blockedNodes: Int,
    val pendingNodes: Int,
    val runningNodes: Int,
    val readyNodes: List<String>,
    val message: String
)
