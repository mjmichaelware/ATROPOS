package atropos.core.agent

class SelfHostBenchmarkService(
    private val selector: SelfHostGoalSelector
) {
    fun history(): List<GoalRunRecord> = selector.allSelfHostRuns()

    fun benchmark(): SelfHostBenchmark {
        val goals = history()
        val completed = goals.count { it.terminalCondition == GoalTerminalCondition.VERIFIED_COMPLETE }
        val failed = goals.count { it.terminalCondition == GoalTerminalCondition.TERMINAL_FAILURE }
        val cancelled = goals.count { it.terminalCondition == GoalTerminalCondition.CANCELLED }
        val recoveryRequired = goals.count { it.status == GoalRunStatus.RECOVERY_REQUIRED }
        val totalContinuations = goals.sumOf { it.continuationCount }
        val avgContinuations = if (goals.isNotEmpty()) totalContinuations.toDouble() / goals.size else 0.0
        val status = when {
            completed == 0 -> "NOT_ACHIEVED"
            failed > 0 || cancelled > 0 || recoveryRequired > 0 -> "PARTIAL_EVIDENCE"
            else -> "NOMINAL_BATCH_PROVEN"
        }
        return SelfHostBenchmark(
            totalGoals = goals.size,
            completed = completed,
            failed = failed,
            cancelled = cancelled,
            recoveryRequired = recoveryRequired,
            totalContinuations = totalContinuations,
            avgContinuations = avgContinuations,
            status = status
        )
    }
}
