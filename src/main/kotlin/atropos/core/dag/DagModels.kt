package atropos.core.dag

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
