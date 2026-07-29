package atropos.core.autonomous

import java.util.Locale

data class AutonomousLearningDecision(
    val accepted: Boolean,
    val reason: String,
    val score: Int = 0
) {
    companion object {
        fun accepted(reason: String, score: Int) = AutonomousLearningDecision(true, reason, score)
        fun refused(reason: String) = AutonomousLearningDecision(false, reason, Int.MAX_VALUE)
    }
}

/**
 * Evidence-only autonomy adaptation.
 *
 * This advisor does not authorise side effects and does not mutate invariants.
 * It only reorders already-eligible backlog tasks and recommends smaller batch
 * windows after repeated repair failures.
 */
class AutonomousLearningAdvisor(
    private val immutableInvariants: Set<String> = setOf(
        "HIG=0",
        "E(DELTA)=0",
        "LOCAL_FIRST=true",
        "PAID_AUTO=false",
        "RAW_SECRET_OUTPUT=false",
        "ROUTE_EXPLAINABLE=true",
        "PROVIDER_REPLACEABLE=true",
        "TERRITORY_BOUND=true",
        "VERIFY_BEFORE_COMMIT=true",
        "ADDRESS_NEVER_BLINDLY_INGEST=true"
    )
) {
    fun rank(
        tasks: List<AutonomousTask>,
        repairs: List<RepairRecord>,
        failovers: List<ProviderFailoverEvent>
    ): List<AutonomousTask> {
        return tasks.map { task -> task to inspect(task, repairs, failovers) }
            .sortedWith(
                compareBy<Pair<AutonomousTask, AutonomousLearningDecision>>(
                    { it.second.score },
                    { it.first.priority.ordinal },
                    { it.first.createdAt },
                    { it.first.id }
                )
            )
            .map { (task, decision) ->
                task.copy(context = task.context + ("learningScore" to decision.score.toString()))
            }
    }

    fun inspect(
        task: AutonomousTask,
        repairs: List<RepairRecord> = emptyList(),
        failovers: List<ProviderFailoverEvent> = emptyList()
    ): AutonomousLearningDecision {
        invariantOverride(task)?.let { invariant ->
            return AutonomousLearningDecision.refused(
                "INVARIANT_OVERRIDE: autonomous learning cannot mutate immutable invariant $invariant"
            )
        }

        val base = task.priority.ordinal * 100
        val retryPenalty = task.retryCount * 25
        val repairSignal = repairScore(task, repairs)
        val failoverSignal = failoverScore(task, failovers)
        val score = (base + retryPenalty + repairSignal + failoverSignal).coerceAtLeast(0)
        return AutonomousLearningDecision.accepted(
            "learned_score=$score priority=${task.priority.name} repair_signal=$repairSignal failover_signal=$failoverSignal",
            score
        )
    }

    fun recommendedBatchSize(requested: Int, repairs: List<RepairRecord>): Int {
        if (requested <= 0) return 0
        val safeRequested = requested
        val recent = repairs.takeLast(10)
        if (recent.isEmpty()) return safeRequested
        val failures = recent.count { !it.success }
        val successes = recent.count { it.success }
        return when {
            failures >= 3 && failures >= successes -> 1
            successes >= 5 && failures == 0 -> safeRequested.coerceAtMost(5)
            else -> safeRequested.coerceAtMost(3)
        }
    }

    private fun invariantOverride(task: AutonomousTask): String? {
        val requested = listOf(
            task.context["overrideInvariant"],
            task.context["invariantOverride"],
            task.context["mutableInvariant"]
        ).filterNotNull()
        return requested.firstNotNullOfOrNull { value ->
            immutableInvariants.firstOrNull { invariant -> invariant.equals(value.trim(), ignoreCase = true) }
        }
    }

    private fun repairScore(task: AutonomousTask, repairs: List<RepairRecord>): Int {
        if (task.kind != AutonomousTaskKind.REPAIR_RETRY) return 0
        val signature = task.context["failureSignature"]?.lowercase(Locale.US)
        val relevant = if (signature.isNullOrBlank()) {
            repairs.takeLast(20)
        } else {
            repairs.filter { it.failureSignature.lowercase(Locale.US).contains(signature) }.takeLast(20)
        }
        if (relevant.isEmpty()) return 0
        val successes = relevant.count { it.success }
        val failures = relevant.count { !it.success }
        return failures * 15 - successes * 20
    }

    private fun failoverScore(task: AutonomousTask, failovers: List<ProviderFailoverEvent>): Int {
        if (task.kind != AutonomousTaskKind.PROVIDER_FAILOVER) return 0
        val primary = task.context["primary"] ?: return 0
        val relevant = failovers.filter { it.primaryProviderId == primary }.takeLast(20)
        if (relevant.isEmpty()) return 0
        val successes = relevant.count { it.success }
        val failures = relevant.count { !it.success }
        return failures * 20 - successes * 10
    }
}
