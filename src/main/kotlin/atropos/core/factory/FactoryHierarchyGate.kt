package atropos.core.factory

import atropos.core.hierarchy.AgentRecord
import atropos.core.hierarchy.HierarchyDispatchContract
import atropos.core.hierarchy.HierarchyDispatchResult
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hierarchy.HierarchyRole
import atropos.core.hr.HrRouterAuditStore
import atropos.core.hr.HrRouterService
import atropos.core.hr.InformationKind
import atropos.core.policy.ActionActor
import atropos.core.verification.CompletionGateReport
import java.time.Instant

/**
 * Thin adapter that enters the canonical hierarchy lifecycle for one factory
 * mutation. The registry remains the sole owner of role and task transitions.
 */
class FactoryHierarchyGate(
    private val registry: HierarchyRegistry = HierarchyRegistry(),
    private val hrRouter: HrRouterService = HrRouterService(),
    /** Optional sub-agent birth channel; absent topology remains a refusal. */
    private val subagentSpawner: SubagentSpawnService? = null
) {
    fun dispatch(
        projectId: String,
        territory: String,
        sourceCoordinate: String,
        capabilities: List<String>
    ): FactoryHierarchyLease {
        check(atropos.core.verification.AdmissionController.validateConfigUpdate(
            mapOf(
                "sourceAuthorityIsImmutable" to true,
                "executionGraphMustBeAcyclic" to true,
                "implementationRequiresVerification" to true
            )
        )) { "factory hierarchy admission refused immutable control-plane invariants" }
        val normalizedTerritory = territory.replace('\\', '/').trim().trimEnd('/')
        require(normalizedTerritory.startsWith(".atropos/generated-projects/")) {
            "factory hierarchy territory must remain under .atropos/generated-projects"
        }
        require(!normalizedTerritory.split('/').contains("..")) {
            "factory hierarchy territory cannot contain parent traversal"
        }
        require(sourceCoordinate.isNotBlank()) { "factory hierarchy source coordinate is required" }
        require(capabilities.isNotEmpty() && capabilities.all(String::isNotBlank)) {
            "factory hierarchy capabilities are required"
        }

        val ownerId = "factory-owner-$projectId"
        val directorId = "factory-director-$projectId"
        val managerId = "factory-manager-$projectId"
        val specialistId = "factory-specialist-$projectId"
        val workerId = "factory-worker-$projectId"
        val taskId = "factory-task-$projectId"
        registry.register(AgentRecord(ownerId, "factory owner", HierarchyRole.HUMAN_OWNER, territoryId = "root"))
        registry.register(
            AgentRecord(
                directorId,
                "factory director",
                HierarchyRole.DIRECTOR,
                territoryId = ".atropos/generated-projects",
                parentManagerId = ownerId,
                capabilities = capabilities
            )
        )
        registry.register(
            AgentRecord(
                managerId,
                "factory manager",
                HierarchyRole.MANAGER,
                territoryId = ".atropos/generated-projects",
                parentManagerId = directorId,
                capabilities = capabilities
            )
        )
        registry.register(
            AgentRecord(
                specialistId,
                "factory specialist",
                HierarchyRole.SPECIALIST,
                parentManagerId = managerId,
                capabilities = capabilities
            )
        )
        registry.register(
            AgentRecord(
                workerId,
                "factory worker",
                HierarchyRole.WORKER,
                parentManagerId = specialistId,
                capabilities = capabilities
            )
        )

        val hrResponse = hrRouter.request(
            sourceOwner = directorId,
            sourceTerr = ".atropos/generated-projects",
            targetOwner = managerId,
            targetTerr = normalizedTerritory,
            kind = InformationKind.TASK_ASSIGNMENT,
            query = "bounded factory task assignment project=$projectId capabilities=${capabilities.joinToString(",")}",
            paths = listOf(normalizedTerritory),
            taskId = taskId,
            sourceCoordinates = listOf(sourceCoordinate),
            needToKnow = "authorize bounded factory hierarchy dispatch",
            sourceRole = HierarchyRole.DIRECTOR,
            targetRole = HierarchyRole.MANAGER
        )
        check(hrResponse.approved) {
            "factory HR Router refused hierarchy dispatch: ${hrResponse.reason}"
        }
        val timeoutAt = Instant.now().plusSeconds(15 * 60)
        val dispatchedTaskIds = mutableListOf<String>()
        dispatchOrRefuse(
            HierarchyDispatchContract(
                taskId = "$taskId-owner",
                parentAuthorityId = ownerId,
                assigneeId = directorId,
                sourceCoordinates = listOf(sourceCoordinate),
                territory = listOf(".atropos/generated-projects"),
                capabilities = capabilities,
                budgetTokens = 1,
                acceptanceCriteria = listOf("bounded factory mutation"),
                rollbackPlan = "remove only the generated project target",
                timeoutAt = timeoutAt
            ),
            dispatchedTaskIds
        )
        dispatchOrRefuse(
            HierarchyDispatchContract(
                taskId = "$taskId-director",
                parentAuthorityId = directorId,
                assigneeId = managerId,
                sourceCoordinates = listOf(sourceCoordinate),
                territory = listOf(normalizedTerritory),
                capabilities = capabilities,
                budgetTokens = 1,
                acceptanceCriteria = listOf("bounded factory mutation"),
                rollbackPlan = "remove only the generated project target",
                timeoutAt = timeoutAt
            ),
            dispatchedTaskIds
        )
        dispatchOrRefuse(
            HierarchyDispatchContract(
                taskId = "$taskId-manager",
                parentAuthorityId = managerId,
                assigneeId = specialistId,
                sourceCoordinates = listOf(sourceCoordinate),
                territory = listOf(normalizedTerritory),
                capabilities = capabilities,
                budgetTokens = 1,
                acceptanceCriteria = listOf("specialist routes bounded factory work"),
                rollbackPlan = "remove only the generated project target",
                timeoutAt = timeoutAt
            ),
            dispatchedTaskIds
        )
        val workerContract = HierarchyDispatchContract(
            taskId = taskId,
            parentAuthorityId = specialistId,
            assigneeId = workerId,
            sourceCoordinates = listOf(sourceCoordinate),
            territory = listOf(normalizedTerritory),
            capabilities = capabilities,
            budgetTokens = 1,
            acceptanceCriteria = listOf("generated source passes independent verification"),
            rollbackPlan = "remove only the generated project target",
            timeoutAt = timeoutAt
        )
        subagentSpawner?.spawn(
            parent = ActionActor.HumanOwner,
            childName = workerId,
            childRole = "factory worker",
            requestedPrefixes = listOf(normalizedTerritory),
            depth = 1
        )
        dispatchOrRefuse(workerContract, dispatchedTaskIds)
        if (!registry.startTask(taskId)) {
            dispatchedTaskIds.asReversed().forEach { dispatchedId ->
                registry.failTask(dispatchedId, "factory hierarchy worker could not start")
            }
            error("factory hierarchy task could not start: $taskId")
        }
        return FactoryHierarchyLease(dispatchedTaskIds, registry, hrResponse.requestId, hrResponse.action.name)
    }

    internal fun completeAfterVerification(
        lease: FactoryHierarchyLease,
        evidence: String,
        gate: CompletionGateReport
    ) {
        require(gate.canComplete && gate.message == "factory completion gate passed") {
            "factory hierarchy completion requires a passing completion gate"
        }
        lease.completeAfterVerifiedGate(evidence)
    }

    private fun dispatchOrRefuse(contract: HierarchyDispatchContract, dispatchedTaskIds: MutableList<String>) {
        when (val result = registry.dispatch(contract)) {
            is HierarchyDispatchResult.Accepted -> dispatchedTaskIds += contract.taskId
            is HierarchyDispatchResult.Refused -> {
                dispatchedTaskIds.asReversed().forEach { dispatchedId ->
                    registry.failTask(dispatchedId, "dependent dispatch refused: ${result.reason}")
                }
                error("factory hierarchy dispatch refused: ${result.reason}")
            }
        }
    }
}

class FactoryHierarchyLease(
    private val taskIds: List<String>,
    private val registry: HierarchyRegistry,
    val hrRequestId: String,
    val hrAction: String
) {
    @Deprecated("Use FactoryHierarchyGate.completeAfterVerification")
    fun complete(evidence: String): Nothing =
        error("factory hierarchy completion requires FactoryHierarchyGate.completeAfterVerification")

    internal fun completeAfterVerifiedGate(evidence: String) {
        require(evidence.isNotBlank()) { "factory hierarchy completion evidence is required" }
        val auditedEvidence = "$evidence hr_request=$hrRequestId hr_action=$hrAction"
        val remaining = taskIds.asReversed().toMutableList()
        try {
            while (remaining.isNotEmpty()) {
                val taskId = remaining.first()
                check(registry.completeTask(taskId, auditedEvidence)) {
                    "factory hierarchy task could not complete: $taskId"
                }
                remaining.removeAt(0)
            }
        } catch (failure: Throwable) {
            remaining.forEach { taskId ->
                runCatching { registry.failTask(taskId, "dependent hierarchy completion failed") }
            }
            throw failure
        }
    }

    fun fail(reason: String) {
        val failure = reason.ifBlank { "factory mutation failed" }
        taskIds.asReversed().forEach { taskId ->
            registry.failTask(taskId, failure)
        }
    }
}
