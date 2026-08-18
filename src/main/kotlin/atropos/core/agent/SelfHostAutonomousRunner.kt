package atropos.core.agent

import atropos.core.verification.GovernedCompileGate
import atropos.core.verification.GovernedCompileGateResult

class SelfHostAutonomousRunner(
    private val service: SelfHostGoalService,
    private val jarLocator: SelfHostRuntimeJarLocator,
    private val jarBuilder: SelfHostCandidateJarBuilder? = null,
    /**
     * The compile gate between "source was mutated" and "a jar may be promoted".
     * Null leaves [SelfHostRunPredicate.COMPILE_GATE_PASSED] unmet, so a caller
     * that omits it can never reach a VERIFIED proof.
     */
    private val compileGate: GovernedCompileGate? = null,
    private val proofBuilder: SelfHostRunProofBuilder? = null,
    private val gitStatusEvidence: SelfHostGitStatusEvidence? = null
) {
    /**
     * Runs the chain, then attaches the operator-facing proof.
     *
     * The proof is built after the chain settles so that every exit — refusal,
     * compile failure, stop-before-promotion, or promotion — carries the same
     * evidence about what is actually on disk.
     */
    /**
     * @param maxAdvances null derives the budget from the DAG once it exists,
     *   which is the only point at which the amount of work is known. A caller
     *   that passes a number keeps it.
     */
    fun run(
        prompt: String,
        phase: String = "11",
        maxAdvances: Int? = null,
        lifecycleEmitter: (String) -> Unit = ::println
    ): SelfHostAutonomousRunResult =
        attachProof(runChain(prompt, phase, maxAdvances, lifecycleEmitter))

    private fun attachProof(result: SelfHostAutonomousRunResult): SelfHostAutonomousRunResult {
        val builder = proofBuilder ?: return result
        val goalId = result.goal?.record?.id ?: return result
        val proof = builder.build(
            goalId = goalId,
            dag = result.goal.dag,
            compileGate = result.compileGate,
            evidenceMarkdownPath = result.evidenceBundle?.markdownPath?.toString(),
            evidenceJsonPath = result.evidenceBundle?.jsonPath?.toString()
        )
        service.addEvidence(goalId, proof.evidenceLine())
        return result.copy(proof = proof)
    }

    private fun runChain(
        prompt: String,
        phase: String,
        maxAdvances: Int?,
        lifecycleEmitter: (String) -> Unit
    ): SelfHostAutonomousRunResult {
        // Narrated rather than merely collected. Every `steps +=` below now
        // reaches a watching operator as it happens instead of arriving as a
        // block after the run has already decided everything.
        val steps = atropos.core.thinking.NarratedSteps()
        steps.outline("starting self-host phase $phase")
        val started = service.startGoal(prompt, phase)
        steps += started.message
        if (!started.ok) return stopped(started, null, null, steps)

        val goalId = started.goal?.record?.id
            ?: return stopped(started.copy(message = "self-host goal start returned no goal"), null, null, steps)
        lifecycleEmitter("ATROPOS_SELF_HOST_RUN_STARTED goal=$goalId")
        steps.outline("ATROPOS_SELF_HOST_RUN_STARTED goal=$goalId")

        // The budget, measured against the graph rather than assumed.
        //
        // It has to be resolved here and not at the call site: `startGoal` is
        // what builds the DAG, so before this line there is no node count to
        // derive from. A run against a three-node bootstrap graph gets fifteen
        // advances; one against a four-hundred-node document graph gets what it
        // needs instead of stopping, silently and successfully, at 25.
        val nodeCount = started.goal?.dag?.nodes?.size ?: 0
        val budget = maxAdvances ?: SelfHostRuntimeRunLimits.forNodeCount(nodeCount)
        steps.outline(
            "run budget $budget advances for $nodeCount DAG " +
                (if (nodeCount == 1) "node" else "nodes")
        )

        var latest = started
        var advances = 0
        var automaticRecoveries = 0
        val recoveryBudget = 2
        while (advances < budget.coerceAtLeast(1)) {
            advances += 1
            // The one line that tells a watching operator the run is alive and
            // where it is. Without it, a long advance is indistinguishable from
            // a hang, and the operator's only move is to kill it.
            // Named for what it is. "advance 3 of 25" reads as "3 of 25 nodes",
            // and an operator who had just attached a 400-atom document
            // reasonably concluded the atomizer had found 25 of them. It is the
            // continuation budget: how many times this loop may iterate before
            // it stops on its own.
            steps.outline("advance $advances of at most $budget (continuation budget, not node count)")
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

        // The compile gate sits between a real source mutation and any jar
        // promotion. A nonzero exit stops the chain here: nothing downstream may
        // treat an uncompilable tree as verified.
        var compileResult: GovernedCompileGateResult? = null
        compileGate?.let { gate ->
            val compiled = gate.verify(goalId)
            compileResult = compiled
            service.addEvidence(goalId, compiled.evidenceLine())
            steps += "compile gate: passed=${compiled.passed} exit=${compiled.exitCode ?: "none"} command=${compiled.commandLine()}"
            if (!compiled.passed) {
                val stopped = service.stopForExternalInput(goalId, compiled.message)
                service.addEvidence(goalId, service.planNextAction(goalId).evidenceLine())
                val bundle = service.exportEvidenceBundle(goalId)
                steps += stopped.message
                steps += bundle.message
                return SelfHostAutonomousRunResult(
                    ok = false,
                    message = "self-host mutated source but the compile gate refused promotion: ${compiled.message}",
                    goal = service.resolveStatusGoal(goalId).goal ?: stopped.goal,
                    promotion = null,
                    evidenceBundle = bundle,
                    steps = steps,
                    compileGate = compiled
                )
            }
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
                    steps = steps,
                    compileGate = compileResult
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
                steps = steps,
                compileGate = compileResult
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
                steps = steps + stopped.message + bundle.message,
                compileGate = compileResult
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
                steps = steps,
                compileGate = compileResult
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
            steps = steps,
            compileGate = compileResult
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
