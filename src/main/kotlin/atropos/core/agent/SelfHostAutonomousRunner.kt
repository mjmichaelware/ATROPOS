package atropos.core.agent

class SelfHostAutonomousRunner(
    private val service: SelfHostGoalService,
    private val jarLocator: SelfHostRuntimeJarLocator,
    private val jarBuilder: SelfHostCandidateJarBuilder? = null,
    private val gitStatusEvidence: SelfHostGitStatusEvidence? = null
) {
    fun run(
        prompt: String,
        phase: String = "11",
        maxAdvances: Int = SelfHostRuntimeRunLimits.maxAdvances(),
        lifecycleEmitter: (String) -> Unit = ::println
    ): SelfHostAutonomousRunResult {
        val steps = mutableListOf<String>()
        val started = service.startGoal(prompt, phase)
        steps += started.message
        if (!started.ok) return stopped(started, null, null, steps)

        val goalId = started.goal?.record?.id
            ?: return stopped(started.copy(message = "self-host goal start returned no goal"), null, null, steps)
        lifecycleEmitter("ATROPOS_SELF_HOST_RUN_STARTED goal=$goalId")
        steps += "ATROPOS_SELF_HOST_RUN_STARTED goal=$goalId"

        var latest = started
        var advances = 0
        var automaticRecoveries = 0
        val recoveryBudget = 2
        while (advances < maxAdvances.coerceAtLeast(1)) {
            advances += 1
            val advanced = service.advanceNextResumableGoal(
                goalId = goalId,
                compactState = "self-host natural-language continuation"
            )
            steps += advanced.message
            latest = advanced
            val record = advanced.goal?.record
            if (!advanced.ok) {
                val persisted = service.resolveStatusGoal(goalId).goal?.record
                if (persisted?.status == GoalRunStatus.RECOVERY_REQUIRED && automaticRecoveries < recoveryBudget) {
                    automaticRecoveries += 1
                    val recovery = service.recoverAndContinue(
                        goalId,
                        compactState = "self-host automatic recovery #$automaticRecoveries"
                    )
                    steps += "automatic recovery #$automaticRecoveries: ${recovery.message}"
                    latest = recovery
                    if (!recovery.ok || recovery.goal?.record?.isTerminal() == true) break
                    continue
                }
                break
            }
            if (record?.isTerminal() == true) break
        }

        val record = service.resolveStatusGoal(goalId).goal?.record ?: latest.goal?.record
        if (record == null) {
            return stopped(SelfHostResult(false, "self-host goal disappeared: $goalId"), null, null, steps)
        }
        gitStatusEvidence?.capture()?.let { statusLine ->
            service.addEvidence(goalId, statusLine)
            steps += statusLine
        }
        if (record.terminalCondition != GoalTerminalCondition.VERIFIED_COMPLETE) {
            service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
            val bundle = service.exportEvidenceBundle(goalId)
            val refreshed = service.resolveStatusGoal(goalId).goal ?: SelfHostGoal(record, latest.goal?.dag)
            steps += bundle.message
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped before promotion: ${record.terminalCondition ?: record.status}",
                goal = refreshed,
                promotion = null,
                evidenceBundle = bundle,
                steps = steps
            )
        }

        var builtCandidateJar: java.nio.file.Path? = null
        jarBuilder?.let { builder ->
            val built = builder.build(goalId)
            service.addEvidence(goalId, built.evidenceLine())
            steps += built.message
            if (!built.ok) {
                val stopped = service.stopForExternalInput(goalId, built.message)
                service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
                val bundle = service.exportEvidenceBundle(goalId)
                steps += stopped.message
                steps += bundle.message
                return SelfHostAutonomousRunResult(
                    ok = false,
                    message = "self-host verified source changes but stopped before jar promotion: ${built.message}",
                    goal = service.resolveStatusGoal(goalId).goal ?: stopped.goal,
                    promotion = null,
                    evidenceBundle = bundle,
                    steps = steps
                )
            }
            builtCandidateJar = built.candidateJar
        }

        val jarPaths = jarLocator.resolve()
        if (!jarPaths.ok || jarPaths.paths == null) {
            service.addEvidence(goalId, "jar_promotion_stop reason=${jarPaths.message}")
            val stopped = service.stopForExternalInput(goalId, jarPaths.message)
            service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
            steps += stopped.message
            val bundle = service.exportEvidenceBundle(goalId)
            val refreshed = service.resolveStatusGoal(goalId).goal ?: stopped.goal
            steps += jarPaths.message
            steps += bundle.message
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host verified source changes but stopped before jar promotion: ${jarPaths.message}",
                goal = refreshed,
                promotion = null,
                evidenceBundle = bundle,
                steps = steps
            )
        }
        val paths = jarPaths.paths ?: run {
            val stopped = service.stopForExternalInput(goalId, "jar paths missing after resolution")
            val bundle = service.exportEvidenceBundle(goalId)
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped before jar promotion: jar paths missing after resolution",
                goal = stopped.goal ?: service.resolveStatusGoal(goalId).goal,
                promotion = null,
                evidenceBundle = bundle,
                steps = steps + stopped.message + bundle.message
            )
        }
        val candidateJar = builtCandidateJar ?: paths.candidateJar

        val prePromotionBundle = service.exportEvidenceBundle(goalId)
        steps += prePromotionBundle.message
        if (!prePromotionBundle.ok) {
            val stopped = service.stopForExternalInput(goalId, prePromotionBundle.message)
            steps += stopped.message
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped before jar promotion: ${prePromotionBundle.message}",
                goal = stopped.goal ?: service.resolveStatusGoal(goalId).goal,
                promotion = null,
                evidenceBundle = prePromotionBundle,
                steps = steps
            )
        }
        val promotion = service.promoteVerifiedJar(
            goalId = goalId,
            candidateJar = candidateJar,
            targetJar = paths.targetJar
        )
        steps += promotion.message
        val stopped = if (!promotion.promoted) {
            service.addEvidence(goalId, "jar_promotion_stop reason=${promotion.message}")
            service.stopForExternalInput(goalId, promotion.message).also { steps += it.message }
        } else {
            null
        }
        service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
        val bundle = service.exportEvidenceBundle(goalId)
        val refreshed = service.resolveStatusGoal(goalId).goal
        if (bundle.message != prePromotionBundle.message) steps += bundle.message
        if (!bundle.ok) {
            return SelfHostAutonomousRunResult(
                ok = false,
                message = "self-host stopped after promotion outcome=${promotion.promoted}; evidence export failed: ${bundle.message}",
                goal = refreshed ?: promotion.goal,
                promotion = promotion,
                evidenceBundle = bundle,
                steps = steps
            )
        }
        return SelfHostAutonomousRunResult(
            ok = promotion.promoted,
            message = if (promotion.promoted) "self-host run promoted verified jar" else "self-host promotion refused: ${promotion.message}",
            goal = refreshed ?: stopped?.goal ?: promotion.goal,
            promotion = promotion,
            evidenceBundle = bundle,
            steps = steps
        )
    }

    private fun stopped(
        result: SelfHostResult,
        promotion: SelfHostPromotionResult?,
        bundle: SelfHostEvidenceBundleResult?,
        steps: List<String>
    ): SelfHostAutonomousRunResult =
        SelfHostAutonomousRunResult(
            ok = false,
            message = result.message,
            goal = result.goal,
            promotion = promotion,
            evidenceBundle = bundle,
            steps = steps
        )
}
