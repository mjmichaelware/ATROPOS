/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.autonomous

import atropos.core.agent.WorkerCodeProposal
import atropos.core.agent.WorkerCodeProposalService
import atropos.core.hierarchy.AgentRecord
import atropos.core.hierarchy.AgentStatus
import atropos.core.hierarchy.HierarchyDispatchContract
import atropos.core.hierarchy.HierarchyRegistry
import atropos.core.hierarchy.HierarchyRole
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.ApiCapability
import atropos.core.provider.ProviderOnboardingService
import atropos.core.provider.ProviderPolicyGate
import atropos.core.provider.StaticProviderDescriptorRegistry
import atropos.core.verification.ProviderProposalMergeReport
import atropos.core.verification.VerifiedCompletionGate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class ProviderWorkerTask(
    val workerId: String,
    val providerId: String,
    val task: String,
    val territory: List<String>,
    val sourceCoordinates: List<String> = listOf("provider-worker/task"),
    val acceptanceCriteria: List<String> = listOf("independent verification passes")
)

data class ProviderWorkerResult(
    val workerId: String,
    val providerId: String,
    val proposal: WorkerCodeProposal?,
    val refusal: String? = null
)

data class ProviderWorkerBatchReport(
    val merged: Boolean,
    val results: List<ProviderWorkerResult>,
    val merge: ProviderProposalMergeReport,
    val refusal: String? = null
) {
    fun render(): String = buildString {
        appendLine("provider workers: ${results.size}")
        appendLine("merge: ${if (merged) "accepted" else "refused"}")
        results.forEach { result ->
            val proposal = result.proposal
            appendLine("  ${result.workerId} provider=${result.providerId} " +
                "accepted=${proposal?.accepted == true} " +
                "evidence=${proposal?.proposalSha256?.take(12) ?: "none"} " +
                "reason=${result.refusal ?: proposal?.reason ?: "none"}")
        }
        refusal?.let { appendLine("refusal: $it") }
        appendLine("merge reason: ${merge.reason}")
    }.trimEnd()
}

/**
 * Director-owned provider fan-out. It dispatches bounded worker contracts
 * through the existing hierarchy, asks the existing proposal service for
 * territory-scoped proposals, and submits the complete set to the existing
 * completion gate. It never applies a worker patch or creates a second
 * orchestrator/verifier.
 */
class ProviderWorkerDirector(
    private val hierarchy: HierarchyRegistry = HierarchyRegistry(),
    private val proposalService: WorkerCodeProposalService = WorkerCodeProposalService(),
    private val completionGate: VerifiedCompletionGate = VerifiedCompletionGate(),
    private val onboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val descriptors: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val policyGate: ProviderPolicyGate = ProviderPolicyGate(
        registry = descriptors,
        healthy = onboarding::healthyProviderIds
    ),
    private val proposalRunner: (ProviderWorkerTask) -> WorkerCodeProposal = { task ->
        proposalService.propose(task.workerId, task.providerId, task.task, task.territory)
    }
) {
    fun run(tasks: List<ProviderWorkerTask>, parentAuthorityId: String = DEFAULT_DIRECTOR_ID): ProviderWorkerBatchReport {
        val normalized = tasks.map { it.copy(providerId = it.providerId.trim().lowercase()) }
        val preflightFailure = validate(normalized, parentAuthorityId)
        if (preflightFailure != null) {
            return refused(normalized, preflightFailure)
        }
        ensureDirector(parentAuthorityId)

        val dispatches = normalized.map { task ->
            val managerId = "provider-manager-${task.providerId}-${shortId()}"
            val workerId = task.workerId
            hierarchy.register(
                AgentRecord(
                    id = managerId,
                    name = managerId,
                    role = HierarchyRole.MANAGER,
                    status = AgentStatus.IDLE,
                    capabilities = capabilities(task.providerId)
                )
            )
            hierarchy.register(
                AgentRecord(
                    id = workerId,
                    name = workerId,
                    role = HierarchyRole.WORKER,
                    status = AgentStatus.IDLE,
                    capabilities = capabilities(task.providerId)
                )
            )
            check(hierarchy.assignManager(workerId, managerId)) {
                "provider worker manager assignment refused: $workerId"
            }
            val managerTaskId = "provider-manager-task-${shortId()}"
            val managerDispatch = hierarchy.dispatch(
                HierarchyDispatchContract(
                    taskId = managerTaskId,
                    parentAuthorityId = parentAuthorityId,
                    assigneeId = managerId,
                    sourceCoordinates = task.sourceCoordinates,
                    territory = task.territory,
                    capabilities = listOf("provider:${task.providerId}"),
                    budgetTokens = BUDGET_TOKENS,
                    acceptanceCriteria = task.acceptanceCriteria,
                    rollbackPlan = "discard unmerged proposal ${task.workerId}"
                )
            )
            val manager = (managerDispatch as? atropos.core.hierarchy.HierarchyDispatchResult.Accepted)
                ?: error((managerDispatch as atropos.core.hierarchy.HierarchyDispatchResult.Refused).reason)
            val workerTaskId = "provider-worker-task-${shortId()}"
            val workerDispatch = hierarchy.dispatch(
                HierarchyDispatchContract(
                    taskId = workerTaskId,
                    parentAuthorityId = manager.contract.assigneeId,
                    assigneeId = workerId,
                    sourceCoordinates = task.sourceCoordinates,
                    territory = task.territory,
                    capabilities = listOf("provider:${task.providerId}"),
                    budgetTokens = BUDGET_TOKENS,
                    acceptanceCriteria = task.acceptanceCriteria,
                    rollbackPlan = "discard unmerged proposal ${task.workerId}"
                )
            )
            val worker = (workerDispatch as? atropos.core.hierarchy.HierarchyDispatchResult.Accepted)
                ?: error((workerDispatch as atropos.core.hierarchy.HierarchyDispatchResult.Refused).reason)
            hierarchy.startTask(worker.contract.taskId)
            DispatchIds(task, managerTaskId, worker.contract.taskId)
        }

        val executor = Executors.newFixedThreadPool(normalized.size.coerceAtMost(MAX_WORKERS))
        val futures: List<Future<ProviderWorkerResult>> = try {
            dispatches.map { dispatch ->
                executor.submit(Callable {
                    runCatching { ProviderWorkerResult(dispatch.task.workerId, dispatch.task.providerId, proposalRunner(dispatch.task)) }
                        .getOrElse { failure ->
                            ProviderWorkerResult(dispatch.task.workerId, dispatch.task.providerId, null, failure.message ?: failure.javaClass.simpleName)
                        }
                })
            }
        } finally {
            executor.shutdown()
        }
        val results = futures.map { future ->
            runCatching { future.get(WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .getOrElse { failure ->
                    ProviderWorkerResult("unknown", "unknown", null, "worker execution failed: ${failure.message ?: failure.javaClass.simpleName}")
                }
        }.sortedBy { it.workerId }
        executor.shutdownNow()
        val merge = completionGate.mergeProviderProposals(results.mapNotNull { it.proposal })
        dispatches.forEach { dispatch ->
            val result = results.firstOrNull { it.workerId == dispatch.task.workerId }
            val accepted = merge.accepted && result?.proposal?.accepted == true
            if (accepted) {
                hierarchy.completeTask(dispatch.workerTaskId, "proposal merged under VerifiedCompletionGate")
                hierarchy.completeTask(dispatch.managerTaskId, "worker proposal completed")
            } else {
                hierarchy.blockTask(dispatch.workerTaskId, result?.refusal ?: result?.proposal?.reason ?: merge.reason)
                hierarchy.blockTask(dispatch.managerTaskId, "worker proposal was not merged")
            }
        }
        return ProviderWorkerBatchReport(merge.accepted, results, merge)
    }

    private fun validate(tasks: List<ProviderWorkerTask>, parentAuthorityId: String): String? {
        if (tasks.isEmpty()) return "at least one provider worker is required"
        if (tasks.size > MAX_WORKERS) return "provider worker batch exceeds $MAX_WORKERS workers"
        if (parentAuthorityId.isBlank()) return "parent Director authority is required"
        val healthy = onboarding.healthyProviderIds()
        val seenWorkers = mutableSetOf<String>()
        val territories = mutableListOf<String>()
        tasks.forEach { task ->
            if (task.workerId.isBlank() || !seenWorkers.add(task.workerId)) return "worker ids must be unique and non-blank"
            if (task.task.isBlank()) return "worker task is required"
            val descriptor = descriptors.getById(task.providerId) ?: return "unknown provider: ${task.providerId}"
            val capability = when {
                descriptor.hasCapability(ApiCapability.CODE) -> ApiCapability.CODE
                descriptor.hasCapability(ApiCapability.REPAIR) -> ApiCapability.REPAIR
                else -> return "provider has no worker capability: ${task.providerId}"
            }
            if (!policyGate.isEligible(task.providerId, capability)) {
                return when {
                    descriptor.isPaid() -> "paid provider requires approval: ${task.providerId}"
                    task.providerId !in healthy -> "provider is not healthy: ${task.providerId}"
                    else -> "provider is not eligible under the canonical policy: ${task.providerId}"
                }
            }
            if (task.territory.isEmpty()) return "worker territory is required: ${task.workerId}"
            task.territory.forEach { territory ->
                val normalized = territory.trim().trim('/').replace('\\', '/')
                if (normalized.isBlank() || normalized == "." || normalized == ".." || normalized.split('/').any { it == ".." }) {
                    return "unsafe worker territory: $territory"
                }
                if (territories.any { overlaps(it, normalized) }) return "worker territories overlap: $normalized"
                territories += normalized
            }
        }
        return null
    }

    private fun ensureDirector(id: String) {
        if (hierarchy.get(id) != null) return
        hierarchy.register(
            AgentRecord(
                id = id,
                name = "provider-director",
                role = HierarchyRole.DIRECTOR,
                territoryId = "root",
                status = AgentStatus.IDLE,
                capabilities = listOf("provider-worker", "code", "repair")
            )
        )
    }

    private fun refused(tasks: List<ProviderWorkerTask>, reason: String): ProviderWorkerBatchReport =
        ProviderWorkerBatchReport(
            merged = false,
            results = tasks.map { ProviderWorkerResult(it.workerId, it.providerId, null, reason) },
            merge = ProviderProposalMergeReport(false, emptyList(), reason),
            refusal = reason
        )

    private fun capabilities(providerId: String) = listOf("provider:$providerId", "code", "repair")
    private fun overlaps(left: String, right: String): Boolean = left == right || left.startsWith("$right/") || right.startsWith("$left/")
    private fun shortId(): String = UUID.randomUUID().toString().take(10)

    private data class DispatchIds(val task: ProviderWorkerTask, val managerTaskId: String, val workerTaskId: String)

    private companion object {
        const val DEFAULT_DIRECTOR_ID = "provider-director"
        const val MAX_WORKERS = 4
        const val BUDGET_TOKENS = 16_000
        const val WORKER_TIMEOUT_SECONDS = 900L
    }
}
