package atropos.core.agent

class SelfHostGoalSelector(
    private val store: GoalRunStore
) {
    fun unfinishedSelfHostRuns(): List<GoalRunRecord> =
        allSelfHostRuns()
            .filter { !it.isTerminal() }
            .sortedWith(
                compareByDescending<GoalRunRecord> { it.status == GoalRunStatus.RECOVERY_REQUIRED }
                    .thenByDescending { it.updatedAt }
            )

    fun allSelfHostRuns(): List<GoalRunRecord> =
        store.listRuns(Int.MAX_VALUE)
            .filter { it.provider == "self-host" }
            .sortedByDescending { it.updatedAt }

    fun resolveSelfHostGoalRecord(
        goalId: String?,
        requireUnfinished: Boolean
    ): GoalRunRecord? {
        if (!goalId.isNullOrBlank()) {
            val resolved = store.resolve(goalId) ?: return null
            if (resolved.provider != "self-host") return null
            if (requireUnfinished && resolved.isTerminal()) return null
            return resolved
        }
        return if (requireUnfinished) {
            unfinishedSelfHostRuns().firstOrNull()
        } else {
            unfinishedSelfHostRuns().firstOrNull()
                ?: allSelfHostRuns().firstOrNull()
        }
    }

    fun missingSelfHostGoalMessage(goalId: String?, requireUnfinished: Boolean, operation: String): String {
        if (!goalId.isNullOrBlank()) {
            val resolved = store.resolve(goalId) ?: return "goal not found: $goalId"
            if (resolved.provider != "self-host") return "goal is not self-host managed: $goalId"
            if (requireUnfinished && resolved.isTerminal()) {
                return "goal already terminal: ${resolved.terminalCondition}"
            }
            return "unable to $operation goal: $goalId"
        }
        return if (requireUnfinished) {
            "no unfinished self-host goals to $operation"
        } else {
            "no self-host goals to $operation"
        }
    }
}
