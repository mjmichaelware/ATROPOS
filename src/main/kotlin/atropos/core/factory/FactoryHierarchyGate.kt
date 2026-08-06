package atropos.core.factory

import atropos.core.hierarchy.AgentRecord
import atropos.core.hierarchy.HierarchyDispatchContract
import atropos.core.hierarchy.HierarchyDispatchResult
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hierarchy.HierarchyRole
import java.time.Instant

/**
 * Thin adapter that enters the canonical hierarchy lifecycle for one factory
 * mutation. The registry remains the sole owner of role and task transitions.
 */
class FactoryHierarchyGate(
    private val registry: HierarchyRegistry = HierarchyRegistry()
) {
    fun dispatch(
        projectId: String,
        territory: String,
        sourceCoordinate: String,
        capabilities: List<String>
    ): FactoryHierarchyLease {
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
        val workerId = "factory-worker-$projectId"
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
                workerId,
                "factory worker",
                HierarchyRole.WORKER,
                parentManagerId = managerId,
                capabilities = capabilities
            )
        )

        val taskId = "factory-task-$projectId"
        val timeoutAt = Instant.now().plusSeconds(15 * 60)
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
            )
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
            )
        )
        val workerContract = HierarchyDispatchContract(
            taskId = taskId,
            parentAuthorityId = managerId,
            assigneeId = workerId,
            sourceCoordinates = listOf(sourceCoordinate),
            territory = listOf(normalizedTerritory),
            capabilities = capabilities,
            budgetTokens = 1,
            acceptanceCriteria = listOf("generated source passes independent verification"),
            rollbackPlan = "remove only the generated project target",
            timeoutAt = timeoutAt
        )
        dispatchOrRefuse(workerContract)
        check(registry.startTask(taskId)) { "factory hierarchy task could not start: $taskId" }
        return FactoryHierarchyLease(taskId, registry)
    }

    private fun dispatchOrRefuse(contract: HierarchyDispatchContract) {
        when (val result = registry.dispatch(contract)) {
            is HierarchyDispatchResult.Accepted -> Unit
            is HierarchyDispatchResult.Refused -> error("factory hierarchy dispatch refused: ${result.reason}")
        }
    }
}

class FactoryHierarchyLease(
    private val taskId: String,
    private val registry: HierarchyRegistry
) {
    fun complete(evidence: String) {
        check(registry.completeTask(taskId, evidence)) { "factory hierarchy task could not complete: $taskId" }
    }

    fun fail(reason: String) {
        registry.failTask(taskId, reason.ifBlank { "factory mutation failed" })
    }
}
