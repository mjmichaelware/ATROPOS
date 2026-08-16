package atropos.core.agent

import atropos.core.ast.PreconditionChecker
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.policy.LifecycleActionProposals

data class BoundedWorkRequest(
    val task: String,
    val smokeCommand: String? = null
)

data class BoundedWorkEnqueueResult(
    val accepted: Boolean,
    val record: AgentQueueRecord?,
    val reason: String
)

/** The authorization boundary for bounded queue work; execution stays in AgentQueueService. */
class BoundedWorkExecutor(
    private val queueService: AgentQueueService,
    private val agencyGate: BoundedAgencyGate,
    private val batchGate: BatchGate = BatchGate()
) {
    fun enqueue(request: BoundedWorkRequest): BoundedWorkEnqueueResult {
        if (request.task.isBlank()) {
            return BoundedWorkEnqueueResult(false, null, "bounded work task must not be blank")
        }
        val decision = agencyGate.evaluate(
            LifecycleActionProposals.queue("enqueue", request.task.trim())
        )
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return BoundedWorkEnqueueResult(false, null, decision.reason)
        }
        val record = queueService.enqueue(request.task.trim(), request.smokeCommand)
        return BoundedWorkEnqueueResult(true, record, "bounded work queued as ${record.id}")
    }

    fun runNext(activeProviderName: String): AgentQueueRunResult {
        val decision = agencyGate.evaluate(LifecycleActionProposals.queue("run", activeProviderName))
        if (decision.disposition != AgencyDisposition.ALLOWED) {
            return AgentQueueRunResult(null, message = decision.reason, ran = false)
        }
        return queueService.runNext(activeProviderName)
    }

    fun evaluateBatch(
        before: Map<String, String>,
        after: Map<String, String>,
        declaredTerritory: Set<String>,
        higValue: Double = 0.0,
        hudValue: Double = 0.0
    ): BatchGateDecision {
        val decision = batchGate.evaluate(before, after, declaredTerritory, higValue, hudValue)
        if (!PreconditionChecker.verifyCommitPrecondition(higValue, hudValue)) {
            return decision.copy(
                passed = false,
                reason = "E(DELTA) commit precondition refused: HIG=$higValue HUD=$hudValue must sum to zero"
            )
        }
        return decision
    }

    companion object {
        fun forService(queueService: AgentQueueService, repoRoot: java.nio.file.Path): BoundedWorkExecutor =
            BoundedWorkExecutor(
                queueService,
                BoundedAgencyGate(ExecutionPolicyEngine(repoRoot))
            )
    }
}
