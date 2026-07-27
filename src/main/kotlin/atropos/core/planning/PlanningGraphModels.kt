package atropos.core.planning

import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.time.Instant
import java.util.UUID

enum class AtomDimension {
    FUNCTIONAL_CONTRACT,
    DEPENDENCY_CONTRACT,
    DATA_LIFECYCLE,
    STATE_MODEL,
    ERROR_MODEL,
    SECURITY_SECRETS,
    TERRITORY_CAPABILITIES,
    OBSERVABILITY_PROVENANCE,
    RESTART_RECOVERY,
    PERFORMANCE_RESOURCES,
    PLATFORM_ENVIRONMENT,
    ACCESSIBILITY_UX,
    TESTS_ACCEPTANCE,
    INTEGRATION_CALL_SITES,
    MIGRATION_COMPATIBILITY,
    ROLLBACK_FAILURE_EVIDENCE
}

data class IngestedSection(
    val id: String,
    val heading: String,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val coordinates: String
)

data class IngestedDocument(
    val documentId: String,
    val projectId: String,
    val sourcePath: String,
    val sha256: String,
    val content: String,
    val sections: List<IngestedSection>,
    val ingestedAt: Instant = Instant.now()
)

data class InternalAtom(
    val id: String = "atom-" + UUID.randomUUID().toString().take(12),
    val projectId: String,
    val documentId: String,
    val sectionId: String,
    val dimension: AtomDimension,
    val statement: String,
    val sourceCoordinates: String,
    val dependencies: List<String> = emptyList(),
    val territory: List<String> = emptyList()
)

data class AuthorityGraph(
    val projectId: String,
    val atoms: List<InternalAtom>,
    val adjacency: Map<String, List<String>>,
    val cyclesAllowed: Boolean = true
)

data class Territory(
    val readPaths: List<String> = emptyList(),
    val writePaths: List<String> = emptyList(),
    val prohibitedPaths: List<String> = emptyList()
)

data class ReadyNode(
    val projectId: String,
    val graphVersion: String,
    val nodeId: String,
    val label: String,
    val territory: Territory,
    val dependencies: List<String>,
    val action: DagNodeAction,
    val actionPayload: String?
)

data class NodeClaim(
    val accepted: Boolean,
    val nodeId: String,
    val executorId: String,
    val territory: Territory,
    val claimToken: String? = null,
    val expiresAt: Instant? = null,
    val reason: String? = null
)

data class ExecutionEvidence(
    val nodeId: String,
    val kind: String,
    val detail: String,
    val relatedPaths: List<String> = emptyList(),
    val recordedAt: Instant = Instant.now()
)

data class EvidenceReceipt(
    val nodeId: String,
    val accepted: Boolean,
    val storedAt: Instant? = null,
    val evidencePath: String? = null,
    val reason: String? = null
)

data class NodeResult(
    val nodeId: String,
    val success: Boolean,
    val message: String,
    val finalState: DagNodeState,
    val result: String? = null,
    val failureReason: String? = null,
    val finishedAt: Instant = Instant.now()
)
